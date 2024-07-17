package nl.qunit.bpmnmeister.engine.pd;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
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
  public void process(Record<String, DefinitionsTrigger> definitionsRecord) {
    String definitionId = definitionsRecord.key();
    if (definitionsRecord.value() instanceof Definitions definitions) {
      processDefinitionsRecord(definitionsRecord, definitions, definitionId);
    } else if (definitionsRecord.value() instanceof StartCommand startCommand) {
      processStartCommandRecord(definitionsRecord, startCommand, definitionId);
    } else {
      throw new IllegalStateException("Unsupported trigger: " + definitionsRecord.value());
    }
  }

  private void processStartCommandRecord(
      Record<String, DefinitionsTrigger> definitionsRecord,
      StartCommand startCommand,
      String definitionId) {
    Integer latestVersion = this.definitionCountByIdStore.get(definitionId);
    if (latestVersion == null) {
      StringBuilder storedDefinitions = new StringBuilder("Available definitions: ");
      try (KeyValueIterator<String, Integer> all = this.definitionCountByIdStore.all()) {
        all.forEachRemaining(e -> storedDefinitions.append(e.key + " "));
        log.error("Process definition not found for key: {}. {}", definitionId, storedDefinitions);
      }
      return;
    }

    ProcessDefinition processDefinition =
        processDefinitionStore.get(new ProcessDefinitionKey(definitionId, latestVersion));
    String startEventId =
        !startCommand.getElementId().equals(Constants.NONE)
            ? startCommand.getElementId()
            : processDefinition
                .getDefinitions()
                .getRootProcess()
                .getFlowElements()
                .getStartEvents()
                .get(0)
                .getId();
    UUID processInstanceKey =
        startCommand.getProcessInstanceKey().equals(Constants.NONE_UUID)
            ? UUID.randomUUID()
            : startCommand.getProcessInstanceKey();
    ProcessInstanceTrigger processInstanceTrigger =
        new StartNewProcessInstanceTrigger(
            startCommand.getRootProcessInstanceKey(),
            processInstanceKey,
            startCommand.getParentProcessInstanceKey(),
            processDefinition,
            startCommand.getParentElementId(),
            startCommand.getParentElementInstanceId(),
            startEventId,
            Constants.NONE,
            startCommand.getVariables());
    context.forward(
        new Record<>(
            startCommand.getRootProcessInstanceKey(),
            processInstanceTrigger,
            definitionsRecord.timestamp()));
  }

  private void processDefinitionsRecord(
      Record<String, DefinitionsTrigger> definitionsRecord,
      Definitions definitions,
      String definitionId) {
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
        context.forward(
            new Record<>(previousKey, deactivationMessage, definitionsRecord.timestamp()));
      }

      definitionCountByIdStore.put(definitionId, newVersion);
      processDefinition =
          new ProcessDefinition(definitions, newVersion, ProcessDefinitionStateEnum.ACTIVE);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, newVersion);
      processDefinitionStore.put(key, processDefinition);
      ProcessDefinitionActivation activationMessage =
          new ProcessDefinitionActivation(processDefinition, ProcessDefinitionStateEnum.ACTIVE);
      context.forward(new Record<>(key, activationMessage, definitionsRecord.timestamp()));
    }
    context.forward(
        new Record<>(
            new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
            processDefinition,
            definitionsRecord.timestamp()));
  }
}
