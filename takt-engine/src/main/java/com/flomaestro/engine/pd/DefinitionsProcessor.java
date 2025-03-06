package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionActivationDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.XmlDefinitionsDTO;
import com.flomaestro.takt.xml.BpmnParser;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
public class DefinitionsProcessor
    implements Processor<String, DefinitionsTriggerDTO, Object, Object> {

  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, String> hashToXmlStore;
  private KeyValueStore<String, Map<String, Integer>> hashVersionPairStore;
  private KeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>> processDefinitionStore;
  private final Map<String, Map<String, Integer>> hashVersionPairCache = new HashMap<>();
  private ProcessDefinitionActivationProcessor processDefinitionActivationProcessor;
  private final Clock clock;

  public DefinitionsProcessor(
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      MessageSchedulerFactory messageSchedulerFactory,
      Clock clock) {
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.messageSchedulerFactory = messageSchedulerFactory;
    this.clock = clock;
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
            tenantNamespaceNameWrapper.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
    processDefinitionActivationProcessor =
        new ProcessDefinitionActivationProcessor(
            tenantNamespaceNameWrapper, messageSchedulerFactory, context, clock);
  }

  @Override
  public void process(Record<String, DefinitionsTriggerDTO> definitionsRecord) {
    if (definitionsRecord.value() instanceof XmlDefinitionsDTO xmlDefinitions) {
      processDefinitionsRecord(
          definitionsRecord.key(), xmlDefinitions);
    } else if (definitionsRecord.value()
        instanceof ProcessDefinitionActivationDTO processDefinitionActivationDTO) {
      processDefinitionActivationProcessor.process(processDefinitionActivationDTO);
    } else {
      throw new IllegalStateException("Unsupported trigger: " + definitionsRecord.value());
    }
  }

  public void processDefinitionsRecord(
      String processDefinitionId, XmlDefinitionsDTO xmlDefinitions) {
    log.info("Processing definitions record for process definition {}", processDefinitionId);
    ParsedDefinitionsDTO parsedDefinition = BpmnParser.parse(xmlDefinitions.getXml());

    Map<String, Integer> hashVersionPairs =
        getHashVersionPairs(parsedDefinition.getDefinitionsKey().getProcessDefinitionId());
    if (hashVersionPairs == null) {
      hashVersionPairs = new HashMap<>();
    }
    Integer version = hashVersionPairs.get(parsedDefinition.getDefinitionsKey().getHash());

    ProcessDefinitionDTO processDefinitionDTO;
    if (version == null) {
      version = hashVersionPairs.size() + 1;

      // New version, create a new ProcessDefinitionDTO and store the relevant information
      String hash = parsedDefinition.getDefinitionsKey().getHash();
      hashVersionPairs.put(hash, version);

      hashVersionPairStore.put(processDefinitionId, hashVersionPairs);
      hashVersionPairCache.put(processDefinitionId, hashVersionPairs);

      hashToXmlStore.put(hash, xmlDefinitions.getXml());

      processDefinitionDTO =
          new ProcessDefinitionDTO(parsedDefinition, version, ProcessDefinitionStateEnum.ACTIVE);

      processDefinitionActivationProcessor.activate(processDefinitionDTO);
    } else {
      // Existing version, do not create a new ProcessDefinitionDTO but return the active version
      ProcessDefinitionKey startKey = new ProcessDefinitionKey(processDefinitionId, 1);
      ProcessDefinitionKey endKey =
          new ProcessDefinitionKey(processDefinitionId, Integer.MAX_VALUE);
      try(KeyValueIterator<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>> range =
          processDefinitionStore.range(startKey, endKey)) {
        range
            .forEachRemaining(
                entry -> {
                  if (entry.value.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
                    context.forward(new Record<>(entry.key, entry.value.value(), clock.millis()));
                  }
                });
      }
    }
  }

  private Map<String, Integer> getHashVersionPairs(String processDefinitionId) {
    Map<String, Integer> stringIntegerMap = hashVersionPairCache.get(processDefinitionId);
    if (stringIntegerMap != null) {
      return stringIntegerMap;
    } else {
      Map<String, Integer> stringIntegerMap1 = hashVersionPairStore.get(processDefinitionId);
      if (stringIntegerMap1 != null) {
        hashVersionPairCache.put(processDefinitionId, stringIntegerMap1);
      }
      return stringIntegerMap1;
    }
  }
}
