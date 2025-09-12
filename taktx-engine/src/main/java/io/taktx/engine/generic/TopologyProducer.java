/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.taktx.Topics;
import io.taktx.dto.Constants;
import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.CorrelationMessageSubscriptions;
import io.taktx.engine.pd.DefinitionMessageSubscriptions;
import io.taktx.engine.pd.DefinitionsProcessor;
import io.taktx.engine.pd.MessageEventProcessor;
import io.taktx.engine.pd.MessageSchedulerFactory;
import io.taktx.engine.pd.ScheduleProcessor;
import io.taktx.engine.pd.Stores;
import io.taktx.engine.pi.DefinitionMapper;
import io.taktx.engine.pi.DefinitionsCache;
import io.taktx.engine.pi.DtoMapper;
import io.taktx.engine.pi.FlowNodeInstancesProcessor;
import io.taktx.engine.pi.Forwarder;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessor;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import io.taktx.serdes.ZippedStringSerde;
import io.taktx.util.TaktUUIDSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes.StringSerde;
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
  public static final Serde<String> ZIPPED_STRING_SERDE = new ZippedStringSerde();
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
  public static final ObjectMapperSerde<UserTaskTriggerDTO> USER_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(UserTaskTriggerDTO.class);
  public static final ObjectMapperSerde<StartCommandDTO> START_COMMAND_SERDE =
      new ObjectMapperSerde<>(StartCommandDTO.class);
  private static final Serde<VariableKeyDTO> VARIABLES_KEY_SERDE =
      new ObjectMapperSerde<>(VariableKeyDTO.class);
  public static final Serde<String> TOPIC_META_KEY_SERDE = new StringSerde();
  public static final Serde<TopicMetaDTO> TOPIC_META_SERDE =
      new ObjectMapperSerde<>(TopicMetaDTO.class);

  private final MessageSchedulerFactory messageSchedulerFactory;
  private final Clock clock;
  private final KeyValueStoreSupplier keyValueStoreSupplier;
  private final DtoMapper dtoMapper;
  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final TaktConfiguration taktConfiguration;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private final IoMappingProcessor ioMappingProcessor;
  private final ProcessingStatistics processingStatistics;
  private final DynamicTopicManager topicManager;
  private final DefinitionsCache definitionsCache;

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
            keyValueStoreSupplier.get(Stores.VERSION_BY_HASH),
            Serdes.String(),
            new ObjectMapperSerde<>(
                (Class<HashMap<String, Integer>>) new HashMap<String, Integer>().getClass())));

    builder.globalTable(
        taktConfiguration.getPrefixed(Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName()),
        Materialized.<ProcessDefinitionKey, ProcessDefinitionDTO>as(
                keyValueStoreSupplier.get(Stores.GLOBAL_PROCESS_DEFINITION))
            .withKeySerde(PROCESS_DEFINITION_KEY_SERDE)
            .withValueSerde(PROCESS_DEFINITION_SERDE));
    builder.globalTable(
        taktConfiguration.getPrefixed(Topics.XML_BY_PROCESS_DEFINITION_ID.getTopicName()),
        Materialized.<ProcessDefinitionKey, String>as(
                keyValueStoreSupplier.get(Stores.XML_BY_PROCESS_DEFINITION_ID))
            .withKeySerde(PROCESS_DEFINITION_KEY_SERDE)
            .withValueSerde(ZIPPED_STRING_SERDE));

    builder.stream(
            taktConfiguration.getPrefixed(Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
            Consumed.with(Serdes.String(), DEFINITIONS_TRIGGER_SERDE))
        .process(
            () ->
                new DefinitionsProcessor(
                    taktConfiguration, messageSchedulerFactory, clock, definitionsCache),
            taktConfiguration.getPrefixed(Stores.VERSION_BY_HASH.getStorename()))
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
                            taktConfiguration.getPrefixed(
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
                            taktConfiguration.getPrefixed(
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
                            taktConfiguration.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE))))
        .branch(
            (key, value) -> key instanceof ProcessDefinitionKey && value instanceof String,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((ProcessDefinitionKey) key, (String) value))
                        .to(
                            taktConfiguration.getPrefixed(
                                Topics.XML_BY_PROCESS_DEFINITION_ID.getTopicName()),
                            Produced.with(PROCESS_DEFINITION_KEY_SERDE, ZIPPED_STRING_SERDE))))
        .branch(
            (key, value) -> key instanceof MessageEventKeyDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKeyDTO) key, (MessageEventDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
                                Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
                            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))));
  }

  private void setupProcessInstanceStream(StreamsBuilder builder) {
    builder.globalTable(
        taktConfiguration.getPrefixed(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName()),
        Materialized.<String, TopicMetaDTO>as(
                keyValueStoreSupplier.get(Stores.TOPIC_META_REQUESTED))
            .withKeySerde(TOPIC_META_KEY_SERDE)
            .withValueSerde(TOPIC_META_SERDE));
    builder.globalTable(
        taktConfiguration.getPrefixed(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName()),
        Materialized.<String, TopicMetaDTO>as(keyValueStoreSupplier.get(Stores.TOPIC_META_ACTUAL))
            .withKeySerde(TOPIC_META_KEY_SERDE)
            .withValueSerde(TOPIC_META_SERDE));

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
            taktConfiguration.getPrefixed(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))
        .process(
            () ->
                new ProcessInstanceProcessor(
                    definitionsCache,
                    definitionMapper,
                    instanceMapper,
                    forwarder,
                    ioMappingProcessor,
                    taktConfiguration,
                    flowNodeInstancesProcessor,
                    clock,
                    dtoMapper,
                    processingStatistics,
                    topicManager),
            taktConfiguration.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()),
            taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
            taktConfiguration.getPrefixed(Stores.VARIABLES.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof ProcessInstanceTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTriggerDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof InstanceUpdateDTO,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((UUID) key, (InstanceUpdateDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
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
                            (key, value, recordContext) ->
                                taktConfiguration.getPrefixed(
                                        Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX)
                                    + value.getExternalTaskId(),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof StartCommandDTO,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommandDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
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
                            taktConfiguration.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE))))
        .branch(
            (key, value) -> value instanceof UserTaskTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((UUID) key, (UserTaskTriggerDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
                                Topics.USER_TASK_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(PROCESS_INSTANCE_KEY_SERDE, USER_TASK_TRIGGER_SERDE))))
        .branch(
            (key, value) -> value instanceof MessageEventDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKeyDTO) key, (MessageEventDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
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
            taktConfiguration.getPrefixed(Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
            Consumed.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))
        .process(
            () -> new MessageEventProcessor(taktConfiguration, clock),
            taktConfiguration.getPrefixed(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()),
            taktConfiguration.getPrefixed(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof DefinitionsTriggerDTO,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((String) key, (DefinitionsTriggerDTO) value))
                        .to(
                            taktConfiguration.getPrefixed(
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
                            taktConfiguration.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupScheduleCommandStream(StreamsBuilder builder) {

    StreamsBuilder stateStore =
        builder
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
                    MESSAGE_SCHEDULE_SERDE))
            .addStateStore(
                keyValueStoreBuilder(
                    keyValueStoreSupplier.get(Stores.SCHEDULES_YEARLY),
                    SCHEDULE_KEY_SERDE,
                    MESSAGE_SCHEDULE_SERDE));

    KStream<ScheduleKeyDTO, MessageScheduleDTO> scheduleCommandStream =
        stateStore.stream(
            taktConfiguration.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
            Consumed.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULE_SERDE));
    KStream<Object, SchedulableMessageDTO> processStream =
        scheduleCommandStream.process(
            () ->
                new ScheduleProcessor(
                    clock,
                    taktConfiguration.inTestMode(),
                    (context, name) ->
                        context.getStateStore(taktConfiguration.getPrefixed("schedules-" + name)),
                    TimeBucket.values()),
            taktConfiguration.getPrefixed(Stores.SCHEDULES_MINUTE.getStorename()),
            taktConfiguration.getPrefixed(Stores.SCHEDULES_HOURLY.getStorename()),
            taktConfiguration.getPrefixed(Stores.SCHEDULES_DAILY.getStorename()),
            taktConfiguration.getPrefixed(Stores.SCHEDULES_WEEKLY.getStorename()),
            taktConfiguration.getPrefixed(Stores.SCHEDULES_YEARLY.getStorename()));
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
                            (key, value, recordContext) ->
                                taktConfiguration.getPrefixed(
                                        Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX)
                                    + value.getExternalTaskId(),
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
                            taktConfiguration.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }
}
