package nl.qunit.bpmnmeister.engine.pd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.DefinitionsTriggerDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParsedDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.XmlDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessDefinitionActivationDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessInstanceTriggerDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.StartCommandDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.StartNewProcessInstanceTriggerDTO;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class NewDefinitionsProcessor
    implements Processor<String, DefinitionsTriggerDTO, Object, Object> {

  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, String> hashToXmlStore;
  private KeyValueStore<String, Map<String, Integer>> hashVersionPairStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionStore;

  public NewDefinitionsProcessor(TenantNamespaceNameWrapper tenantNamespaceNameWrapper) {
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.hashToXmlStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.XML_BY_HASH.getStorename()));
    this.hashVersionPairStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.VERSION_BY_HASH.getStorename()));
    this.processDefinitionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_DEFINITION.getStorename()));
  }

  @Override
  public void process(Record<String, DefinitionsTriggerDTO> definitionsRecord) {
    if (definitionsRecord.value() instanceof XmlDefinitionsDTO xmlDefinitions) {
      processDefinitionsRecord(
          definitionsRecord.key(), xmlDefinitions, definitionsRecord.timestamp());
    } else if (definitionsRecord.value() instanceof StartCommandDTO startCommand) {
      processStartCommandRecord(definitionsRecord, startCommand);
    } else {
      throw new IllegalStateException("Unsupported trigger: " + definitionsRecord.value());
    }
  }

  public void processDefinitionsRecord(
      String processDefinitionId, XmlDefinitionsDTO xmlDefinitions, long timestamp) {

    ParsedDefinitionsDTO parsedDefinition = BpmnParser.parse(xmlDefinitions.getXml());

    Map<String, Integer> hashVersionPairs =
        hashVersionPairStore.get(parsedDefinition.getDefinitionsKey().getProcessDefinitionId());
    if (hashVersionPairs == null) {
      hashVersionPairs = new HashMap<>();
    }
    Integer version = hashVersionPairs.get(parsedDefinition.getDefinitionsKey().getHash());

    ProcessDefinitionDTO processDefinitionDTO;
    ProcessDefinitionKey processDefinitionKey;
    if (version == null) {
      version = hashVersionPairs.size() + 1;

      // New version, create a new ProcessDefinitionDTO and store the relevant information
      String hash = parsedDefinition.getDefinitionsKey().getHash();
      hashVersionPairs.put(hash, version);

      hashVersionPairStore.put(processDefinitionId, hashVersionPairs);
      hashToXmlStore.put(hash, xmlDefinitions.getXml());

      processDefinitionDTO =
          new ProcessDefinitionDTO(parsedDefinition, version, ProcessDefinitionStateEnum.ACTIVE);
      processDefinitionKey = ProcessDefinitionKey.of(processDefinitionDTO);
      processDefinitionStore.put(processDefinitionKey, processDefinitionDTO);

    } else {
      // Existing version, do not create a new ProcessDefinitionDTO but get the latest version
      version = hashVersionPairs.size();
      processDefinitionDTO =
          processDefinitionStore.get(new ProcessDefinitionKey(processDefinitionId, version));
      processDefinitionKey = ProcessDefinitionKey.of(processDefinitionDTO);
    }

    if (version > 1) {
      // Not the first version, deactivate the previous active version
      ProcessDefinitionDTO previousActiveVersion =
          processDefinitionStore.get(new ProcessDefinitionKey(processDefinitionId, version - 1));
      ProcessDefinitionDTO deactivatedPreviousActiveVersion =
          new ProcessDefinitionDTO(
              previousActiveVersion.getDefinitions(),
              previousActiveVersion.getVersion(),
              ProcessDefinitionStateEnum.INACTIVE);
      ProcessDefinitionKey previousProcessDefinitionKey =
          ProcessDefinitionKey.of(deactivatedPreviousActiveVersion);
      processDefinitionStore.put(previousProcessDefinitionKey, deactivatedPreviousActiveVersion);
      ProcessDefinitionActivationDTO deactivationMessage =
          new ProcessDefinitionActivationDTO(
              previousActiveVersion, ProcessDefinitionStateEnum.INACTIVE);
      context.forward(new Record<>(previousProcessDefinitionKey, deactivationMessage, timestamp));
    }

    ProcessDefinitionActivationDTO activationMessage =
        new ProcessDefinitionActivationDTO(processDefinitionDTO, ProcessDefinitionStateEnum.ACTIVE);
    context.forward(new Record<>(processDefinitionKey, activationMessage, timestamp));

    context.forward(new Record<>(processDefinitionKey, processDefinitionDTO, timestamp));
  }

  private void processStartCommandRecord(
      Record<String, DefinitionsTriggerDTO> definitionsRecord, StartCommandDTO startCommand) {
    Map<String, Integer> stringIntegerMap = hashVersionPairStore.get(definitionsRecord.key());
    // Get highest value from stringIntegerMap
    Integer latestVersion = stringIntegerMap.size();
    if (latestVersion == null) {
      log.warn("No process definition found for key {}", definitionsRecord.key());
      return;
    }

    ProcessDefinitionDTO processDefinition =
        processDefinitionStore.get(
            new ProcessDefinitionKey(definitionsRecord.key(), latestVersion));
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
    ProcessInstanceTriggerDTO processInstanceTrigger =
        new StartNewProcessInstanceTriggerDTO(
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
}
