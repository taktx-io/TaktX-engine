package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionActivationDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartNewProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.XmlDefinitionsDTO;
import com.flomaestro.takt.xml.BpmnParser;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DefinitionsProcessor
    implements Processor<String, DefinitionsTriggerDTO, Object, Object> {

  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, String> hashToXmlStore;
  private KeyValueStore<String, Map<String, Integer>> hashVersionPairStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionStore;
  private ProcessDefinitionActivationProcessor processDefinitionActivationProcessor;

  public DefinitionsProcessor(
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      MessageSchedulerFactory messageSchedulerFactory) {
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.messageSchedulerFactory = messageSchedulerFactory;
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
    processDefinitionActivationProcessor =
        new ProcessDefinitionActivationProcessor(
            tenantNamespaceNameWrapper, messageSchedulerFactory, context);
  }

  @Override
  public void process(Record<String, DefinitionsTriggerDTO> definitionsRecord) {
    if (definitionsRecord.value() instanceof XmlDefinitionsDTO xmlDefinitions) {
      processDefinitionsRecord(
          definitionsRecord.key(), xmlDefinitions, definitionsRecord.timestamp());
    } else if (definitionsRecord.value() instanceof StartCommandDTO startCommand) {
      processStartCommandRecord(definitionsRecord, startCommand);
    } else if (definitionsRecord.value()
        instanceof ProcessDefinitionActivationDTO processDefinitionActivationDTO) {
      processDefinitionActivationProcessor.process(processDefinitionActivationDTO);
    } else {
      throw new IllegalStateException("Unsupported trigger: " + definitionsRecord.value());
    }
  }

  public void processDefinitionsRecord(
      String processDefinitionId, XmlDefinitionsDTO xmlDefinitions, long timestamp) {
    log.info("Processing definitions record for process definition {}", processDefinitionId);
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
      processDefinitionActivationProcessor.activate(processDefinitionKey);
    } else {
      // Existing version, do not create a new ProcessDefinitionDTO but return the active version
      ProcessDefinitionKey startKey = new ProcessDefinitionKey(processDefinitionId, 1);
      ProcessDefinitionKey endKey =
          new ProcessDefinitionKey(processDefinitionId, Integer.MAX_VALUE);
      processDefinitionStore
          .range(startKey, endKey)
          .forEachRemaining(
              entry -> {
                if (entry.value.getState() == ProcessDefinitionStateEnum.ACTIVE) {
                  log.info(
                      "Forwarding active process definition for process definition {}", entry.key);
                  context.forward(new Record(entry.key, entry.value, Instant.now().toEpochMilli()));
                }
              });
    }
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
            List.of(startEventId),
            startCommand.getVariables());
    log.info("Starting process instance for process definition {}", definitionsRecord.key());
    context.forward(
        new Record<>(processInstanceKey, processInstanceTrigger, definitionsRecord.timestamp()));
  }
}
