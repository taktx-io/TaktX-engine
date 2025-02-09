package com.flomaestro.engine.generic;

import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.pd.CorrelationMessageSubscriptions;
import com.flomaestro.engine.pd.DefinitionMessageSubscriptions;
import com.flomaestro.engine.pd.DefinitionsProcessor;
import com.flomaestro.engine.pd.MessageEventProcessor;
import com.flomaestro.engine.pd.MessageSchedulerFactory;
import com.flomaestro.engine.pd.ScheduleProcessor;
import com.flomaestro.engine.pd.Stores;
import com.flomaestro.engine.pi.DefinitionMapper;
import com.flomaestro.engine.pi.DtoMapper;
import com.flomaestro.engine.pi.FlowNodeInstancesProcessor;
import com.flomaestro.engine.pi.Forwarder;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessInstanceProcessor;
import com.flomaestro.engine.pi.processor.IoMappingProcessor;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.util.TaktUUIDSerde;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

@ApplicationScoped
@RequiredArgsConstructor
public class TopologyProducer {

  public static final ObjectMapperSerde<MessageEventDTO> MESSAGE_EVENT_SERDE =
      new ObjectMapperSerde<>(MessageEventDTO.class);
  public static final ObjectMapperSerde<DefinitionMessageSubscriptions>
      DEFINITION_SUBSCRIPTIONS_SERDE =
      new ObjectMapperSerde<>(DefinitionMessageSubscriptions.class);
  public static final ObjectMapperSerde<CorrelationMessageSubscriptions>
      CORRELATION_SUBSCRIPTIONS_SERDE =
      new ObjectMapperSerde<>(CorrelationMessageSubscriptions.class);
  public static final ObjectMapperSerde<ProcessingStatisticsDTO> PROCESSING_STATISTICS_SERDE =
      new ObjectMapperSerde<>(ProcessingStatisticsDTO.class);
  public static final ObjectMapperSerde<ProcessDefinitionKey> PROCESS_DEFINITION_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionKey.class);
  public static final ObjectMapperSerde<ScheduleKeyDTO> SCHEDULE_KEY_SERDE =
      new ObjectMapperSerde<>(ScheduleKeyDTO.class);
  public static final ObjectMapperSerde<MessageEventKeyDTO> MESSAGE_EVENT_KEY_SERDE =
      new ObjectMapperSerde<>(MessageEventKeyDTO.class);
  public static final Serde<UUID> PROCESS_INSTANCE_KEY_SERDE = new TaktUUIDSerde();
  public static final Serde<FlowNodeInstanceKeyDTO> FLOW_NODE_INSTANCE_KEY_SERDE =
      new ObjectMapperSerde<>(FlowNodeInstanceKeyDTO.class);
  public static final ObjectMapperSerde<MessageScheduleDTO> MESSAGE_SCHEDULE_SERDE =
      new ObjectMapperSerde<>(MessageScheduleDTO.class);
  public static final ObjectMapperSerde<ProcessInstanceTriggerDTO> PROCESS_INSTANCE_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceTriggerDTO.class);
  public static final ObjectMapperSerde<ProcessDefinitionDTO> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionDTO.class);
  public static final ObjectMapperSerde<JsonNode> VARIABLES_SERDE =
      new ObjectMapperSerde<>(JsonNode.class);
  public static final ObjectMapperSerde<DefinitionsTriggerDTO> DEFINITIONS_TRIGGER_SERDE =
      new ObjectMapperSerde<>(DefinitionsTriggerDTO.class);
  public static final ObjectMapperSerde<ProcessInstanceDTO> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceDTO.class);
  public static final ObjectMapperSerde<InstanceUpdateDTO> INSTANCE_UPDATE_SERDE =
      new ObjectMapperSerde<>(InstanceUpdateDTO.class);
  public static final ObjectMapperSerde<FlowNodeInstanceDTO> FLOW_NODE_INSTANCE_SERDE =
      new ObjectMapperSerde<>(FlowNodeInstanceDTO.class);
  public static final ObjectMapperSerde<ExternalTaskTriggerDTO> EXTERNAL_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ExternalTaskTriggerDTO.class);
  public static final ObjectMapperSerde<StartCommandDTO> START_COMMAND_SERDE =
      new ObjectMapperSerde<>(StartCommandDTO.class);
  private static final Serde<VariableKeyDTO> VARIABLES_KEY_SERDE =
      new ObjectMapperSerde<>(VariableKeyDTO.class);

  private final MessageSchedulerFactory messageSchedulerFactory;
  private final Clock clock;
  private final KeyValueStoreSupplier keyValueStoreSupplier;
  private final DtoMapper dtoMapper;
  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private final IoMappingProcessor ioMappingProcessor;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    setupNewDefinitionStream(builder);

    setupMessageStream(builder);

    setupScheduleCommandStream(builder);

    setupProcessInstanceStream(builder);

    return builder.build();
  }

  private void setupNewDefinitionStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.XML_BY_HASH), Serdes.String(), Serdes.String()));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.VERSION_BY_HASH),
            Serdes.String(),
            new ObjectMapperSerde<>(
                (Class<HashMap<String, Integer>>) new HashMap<String, Integer>().getClass())));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.PROCESS_DEFINITION),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_DEFINITION_SERDE));

    // Define the global table
    builder.globalTable(
        tenantNamespaceNameWrapper.getPrefixed(
            Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName()),
        Materialized.<ProcessDefinitionKey, ProcessDefinitionDTO>as(
                keyValueStoreSupplier.get(Stores.GLOBAL_PROCESS_DEFINITION))
            .withKeySerde(PROCESS_DEFINITION_KEY_SERDE)
            .withValueSerde(PROCESS_DEFINITION_SERDE));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
            Consumed.with(Serdes.String(), DEFINITIONS_TRIGGER_SERDE))
        .process(
            () ->
                new DefinitionsProcessor(
                    tenantNamespaceNameWrapper, messageSchedulerFactory, clock),
            tenantNamespaceNameWrapper.getPrefixed(Stores.XML_BY_HASH.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.VERSION_BY_HASH.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_DEFINITION.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof ProcessDefinitionDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair(
                                    (ProcessDefinitionKey) key, (ProcessDefinitionDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName()),
                            Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_DEFINITION_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))))
        .branch(
            (key, value) -> key instanceof ScheduleKeyDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((ScheduleKeyDTO) key, (MessageScheduleDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.SCHEDULE_COMMANDS.getTopicName()),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE))))
        .branch(
            (key, value) -> key instanceof MessageEventKeyDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKeyDTO) key, (MessageEventDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
                            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))));
  }

  private void setupProcessInstanceStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.PROCESS_INSTANCE),
            PROCESS_INSTANCE_KEY_SERDE,
            PROCESS_INSTANCE_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.FLOW_NODE_INSTANCE),
            FLOW_NODE_INSTANCE_KEY_SERDE,
            FLOW_NODE_INSTANCE_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.VARIABLES), VARIABLES_KEY_SERDE, VARIABLES_SERDE));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))
        .process(
            () ->
                new ProcessInstanceProcessor(
                    ioMappingProcessor,
                    dtoMapper,
                    definitionMapper,
                    instanceMapper,
                    forwarder,
                    tenantNamespaceNameWrapper,
                    flowNodeInstancesProcessor,
                    clock),
            tenantNamespaceNameWrapper.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof ProcessingStatisticsDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((String) key, (ProcessingStatisticsDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESSING_STATISTICS_TOPIC.getTopicName()),
                            Produced.with(Serdes.String(), PROCESSING_STATISTICS_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof InstanceUpdateDTO,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((UUID) key, (InstanceUpdateDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.INSTANCE_UPDATE_TOPIC.getTopicName()),
                            Produced.with(PROCESS_INSTANCE_KEY_SERDE, INSTANCE_UPDATE_SERDE))))
        .branch(
            (key, value) -> value instanceof ExternalTaskTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ExternalTaskTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof StartCommandDTO,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommandDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(Serdes.String(), START_COMMAND_SERDE))))
        .branch(
            (key, value) -> key instanceof ScheduleKeyDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((ScheduleKeyDTO) key, (MessageScheduleDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.SCHEDULE_COMMANDS.getTopicName()),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE))))
        .branch(
            (key, value) -> value instanceof MessageEventDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKeyDTO) key, (MessageEventDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
                            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))));
  }

  private void setupMessageStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.DEFINITION_MESSAGE_SUBSCRIPTION),
            MESSAGE_EVENT_KEY_SERDE,
            DEFINITION_SUBSCRIPTIONS_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.CORRELATION_MESSAGE_SUBSCRIPTION),
            MESSAGE_EVENT_KEY_SERDE,
            CORRELATION_SUBSCRIPTIONS_SERDE));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
            Consumed.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))
        .process(
            () -> new MessageEventProcessor(tenantNamespaceNameWrapper, clock),
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof DefinitionsTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((String) key, (DefinitionsTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(Serdes.String(), DEFINITIONS_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupScheduleCommandStream(StreamsBuilder builder) {

    StreamsBuilder stateStore =
        builder.addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_SECOND),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE))
            .addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_MINUTE),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE))
            .addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_HOURLY),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE))
            .addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_DAILY),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE))
            .addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_WEEKLY),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE));

    KStream<ScheduleKeyDTO, MessageScheduleDTO> scheduleCommandStream =
        stateStore.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
            Consumed.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE));
    KStream<Object, SchedulableMessageDTO> processStream =
        scheduleCommandStream.process(
            () -> new ScheduleProcessor(clock, tenantNamespaceNameWrapper),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_SECOND.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_MINUTE.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_HOURLY.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_DAILY.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_WEEKLY.getStorename())
        );
    processStream
        .split()
        .branch(
            (k, v) -> v instanceof ExternalTaskTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ExternalTaskTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE))))
        .branch(
            (k, v) -> v instanceof ProcessInstanceTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTriggerDTO) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }
}
