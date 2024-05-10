package nl.qunit.bpmnmeister.engine.pd;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DefinitionsProcessor implements Processor<String, DefinitionsTrigger, Object, Object> {

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, Integer> definitionCountByIdStore;
  private KeyValueStore<String, Definitions> xmlByHashStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processDefinitionStore;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionCountByIdStore = context.getStateStore(Stores.DEFINITION_COUNT_BY_ID_STORE_NAME);
    this.processDefinitionStore = context.getStateStore(Stores.PROCESS_DEFINITION_STORE_NAME);
    this.xmlByHashStore = context.getStateStore(Stores.XML_BY_HASH_STORE_NAME);
  }

  @Override
  public void process(Record<String, DefinitionsTrigger> record) {
    String definitionId = record.key();
    if (record.value() instanceof Definitions definitions) {
      processDefinitionsRecord(record, definitions, definitionId);
    } else if (record.value() instanceof StartCommand startCommand) {
      processStartCommandRecord(record, startCommand, definitionId);
    } else {
      throw new RuntimeException("Unsupported trigger: " + record.value());
    }
  }

  private void processStartCommandRecord(
      Record<String, DefinitionsTrigger> record, StartCommand startCommand, String definitionId) {
    Integer latestVersion = this.definitionCountByIdStore.get(definitionId);
    if (latestVersion == null) {
      StringBuilder storedDefinitions = new StringBuilder("Available definitions: ");
      this.definitionCountByIdStore
          .all()
          .forEachRemaining(e -> storedDefinitions.append(e.key + " "));
      log.error("Process definition not found for key: {}. {}", definitionId, storedDefinitions);
      return;
    }

    ProcessDefinition processDefinition =
        processDefinitionStore.get(new ProcessDefinitionKey(definitionId, latestVersion));
    StartEvent startEvent =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getStartEvents()
            .get(0);
    ProcessInstanceKey parentProcessInstanceKey =
        new ProcessInstanceKey(startCommand.getParentProcessInstanceId().getId());
    ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    ProcessInstanceTrigger processInstanceTrigger =
        new StartNewProcessInstanceTrigger(
            processInstanceKey,
            parentProcessInstanceKey,
            processDefinition,
            startCommand.getParentElementId(),
            startEvent.getId(),
            Constants.NONE,
            startCommand.getVariables());
    context.forward(new Record<>(processInstanceKey, processInstanceTrigger, record.timestamp()));
  }

  private void processDefinitionsRecord(
      Record<String, DefinitionsTrigger> record, Definitions definitions, String definitionId) {
    String hash = definitions.getDefinitionsKey().getHash();
    ProcessDefinition processDefinition;
    if (xmlByHashStore.get(hash) != null) {
      // known hash, do not store but forward as existing processdefinition
      int version = definitionCountByIdStore.get(definitionId);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, version);
      processDefinition = processDefinitionStore.get(key);
    } else {
      // unknwn hash, store and forward as new process definition
      xmlByHashStore.put(hash, definitions);
      Integer existingVersion = definitionCountByIdStore.get(definitionId);
      if (existingVersion == null) {
        existingVersion = 0;
      }
      ProcessDefinitionKey previousKey = new ProcessDefinitionKey(definitionId, existingVersion);
      int newVersion = existingVersion + 1;

      // deactivated definition and send deactivatinon message for previous version
      ProcessDefinition previousDefinition = processDefinitionStore.get(previousKey);
      if (previousDefinition != null) {
        ProcessDefinition dactivatedProcessDefinition =
            new ProcessDefinition(
                definitions, existingVersion, ProcessDefinitionStateEnum.INACTIVE);
        ProcessDefinitionActivation deactivationMessage =
            new ProcessDefinitionActivation(
                previousDefinition, ProcessDefinitionStateEnum.INACTIVE);
        processDefinitionStore.put(previousKey, dactivatedProcessDefinition);
        context.forward(new Record<>(previousKey, deactivationMessage, record.timestamp()));
      }

      definitionCountByIdStore.put(definitionId, newVersion);
      processDefinition =
          new ProcessDefinition(definitions, newVersion, ProcessDefinitionStateEnum.ACTIVE);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, newVersion);
      processDefinitionStore.put(key, processDefinition);
      ProcessDefinitionActivation activationMessage =
          new ProcessDefinitionActivation(processDefinition, ProcessDefinitionStateEnum.ACTIVE);
      context.forward(new Record<>(key, activationMessage, record.timestamp()));
    }
    context.forward(
        new Record<>(
            new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
            processDefinition,
            record.timestamp()));
  }
}
