package nl.qunit.bpmnmeister.engine.pd;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
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

public class DefinitionsProcessor implements Processor<String, DefinitionsTrigger, Object, Object> {

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, Integer> definitionCountByIdStore;
  private KeyValueStore<String, DefinitionsDTO> definitionsByHashStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionStore;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionCountByIdStore = context.getStateStore(Stores.DEFINITION_COUNT_BY_ID_STORE_NAME);
    this.processDefinitionStore = context.getStateStore(Stores.PROCESS_DEFINITION_STORE_NAME);
    this.definitionsByHashStore = context.getStateStore(Stores.XML_BY_HASH_STORE_NAME);
  }

  @Override
  public void process(Record<String, DefinitionsTrigger> definitionsRecord) {
    if (definitionsRecord.value() instanceof DefinitionsDTO definitions) {
      processDefinitionsRecord(definitionsRecord, definitions);
    } else if (definitionsRecord.value() instanceof StartCommand startCommand) {
      processStartCommandRecord(definitionsRecord, startCommand);
    } else {
      throw new IllegalStateException("Unsupported trigger: " + definitionsRecord.value());
    }
  }

  private void processStartCommandRecord(
      Record<String, DefinitionsTrigger> definitionsRecord, StartCommand startCommand) {
    String definitionId = definitionsRecord.key();
    Integer latestVersion = this.definitionCountByIdStore.get(definitionId);
    if (latestVersion == null) {
      StringBuilder storedDefinitions = new StringBuilder("Available definitions: ");
      try (KeyValueIterator<String, Integer> all = this.definitionCountByIdStore.all()) {
        all.forEachRemaining(e -> storedDefinitions.append(e.key + " "));
      }
      return;
    }

    ProcessDefinitionDTO processDefinition =
        processDefinitionStore.get(new ProcessDefinitionKey(definitionId, latestVersion));
    String startEventId =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getStartNode(startCommand.getElementId())
            .getId();
    UUID processInstanceKey =
        startCommand.getProcessInstanceKey().equals(Constants.NONE_UUID)
            ? UUID.randomUUID()
            : startCommand.getProcessInstanceKey();
    ProcessInstanceTrigger processInstanceTrigger =
        new StartNewProcessInstanceTrigger(
            processInstanceKey,
            startCommand.getParentProcessInstanceKey(),
            startCommand.getParentElementIdPath(),
            startCommand.getParentElementInstancePath(),
            processDefinition,
            startEventId,
            startCommand.getVariables());
    context.forward(
        new Record<>(processInstanceKey, processInstanceTrigger, definitionsRecord.timestamp()));
  }

  private void processDefinitionsRecord(
      Record<String, DefinitionsTrigger> definitionsRecord, DefinitionsDTO definitions) {
    String hash = definitions.getDefinitionsKey().getHash();
    String definitionId = definitionsRecord.key();

    Integer latestExistingVersion = definitionCountByIdStore.get(definitionId);
    DefinitionsDTO definitionsDTO = definitionsByHashStore.get(hash);
    if (latestExistingVersion == null && definitionsDTO == null) {
      // first version
      Integer newVersion = 1;
      definitionsByHashStore.put(hash, definitions);
      definitionCountByIdStore.put(definitionId, newVersion);
      ProcessDefinitionDTO processDefinition =
          new ProcessDefinitionDTO(definitions, newVersion, ProcessDefinitionStateEnum.ACTIVE);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, newVersion);
      processDefinitionStore.put(key, processDefinition);
      ProcessDefinitionActivation activationMessage =
          new ProcessDefinitionActivation(processDefinition, ProcessDefinitionStateEnum.ACTIVE);
      context.forward(new Record<>(key, activationMessage, definitionsRecord.timestamp()));
      context.forward(
          new Record<>(
              new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
              processDefinition,
              definitionsRecord.timestamp()));

    } else {
      // Versions already exist
      ProcessDefinitionKey existingKey =
          new ProcessDefinitionKey(definitionId, latestExistingVersion);

      // known hash, check if it corresponds to the latest version
      ProcessDefinitionDTO processDefinition = processDefinitionStore.get(existingKey);
      if (hash.equals(processDefinition.getDefinitions().getDefinitionsKey().getHash())) {
        // hash corresponds to latest version, forward as is
        context.forward(
            new Record<>(
                new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
                processDefinition,
                definitionsRecord.timestamp()));
      } else {
        // different hash, store and process and forward as a new version and deactivate previous

        // deactivated definition and send deactivatinon message for previous version
        ProcessDefinitionDTO previousDefinition = processDefinitionStore.get(existingKey);
        if (previousDefinition != null) {
          ProcessDefinitionDTO dactivatedProcessDefinition =
              new ProcessDefinitionDTO(
                  definitions, latestExistingVersion, ProcessDefinitionStateEnum.INACTIVE);
          ProcessDefinitionActivation deactivationMessage =
              new ProcessDefinitionActivation(
                  previousDefinition, ProcessDefinitionStateEnum.INACTIVE);
          processDefinitionStore.put(existingKey, dactivatedProcessDefinition);

          Integer newVersion = latestExistingVersion + 1;
          definitionsByHashStore.put(hash, definitions);
          definitionCountByIdStore.put(definitionId, newVersion);
          processDefinition =
              new ProcessDefinitionDTO(definitions, newVersion, ProcessDefinitionStateEnum.ACTIVE);
          ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, newVersion);
          processDefinitionStore.put(key, processDefinition);
          ProcessDefinitionActivation activationMessage =
              new ProcessDefinitionActivation(processDefinition, ProcessDefinitionStateEnum.ACTIVE);
          context.forward(new Record<>(key, activationMessage, definitionsRecord.timestamp()));
          context.forward(
              new Record<>(
                  new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
                  processDefinition,
                  definitionsRecord.timestamp()));
        }
      }
    }
  }
}
