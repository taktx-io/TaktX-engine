package nl.qunit.bpmnmeister.engine.generic;

import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.pd.CorrelationMessageSubscriptions;
import nl.qunit.bpmnmeister.engine.pd.DefinitionMessageSubscriptions;
import nl.qunit.bpmnmeister.engine.pd.DefinitionsMapper;
import nl.qunit.bpmnmeister.engine.pd.DefinitionsProcessor;
import nl.qunit.bpmnmeister.engine.pd.KeyValueStoreSupplier;
import nl.qunit.bpmnmeister.engine.pd.MessageEventProcessor;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pd.ProcessDefinitionActivationProcessor;
import nl.qunit.bpmnmeister.engine.pd.ScheduleProcessor;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.DefinitionMapper;
import nl.qunit.bpmnmeister.engine.pi.FlowNodeInstancesProcessor;
import nl.qunit.bpmnmeister.engine.pi.Forwarder;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

@ApplicationScoped
@RequiredArgsConstructor
public class TopologyProducer {
  public static final ObjectMapperSerde<MessageEvent> MESSAGE_EVENT_SERDE =
      new ObjectMapperSerde<>(MessageEvent.class);
  public static final ObjectMapperSerde<DefinitionMessageSubscriptions>
      DEFINITION_SUBSCRIPTIONS_SERDE =
          new ObjectMapperSerde<>(DefinitionMessageSubscriptions.class);
  public static final ObjectMapperSerde<CorrelationMessageSubscriptions>
      CORRELATION_SUBSCRIPTIONS_SERDE =
          new ObjectMapperSerde<>(CorrelationMessageSubscriptions.class);
  public static final ObjectMapperSerde<ProcessDefinitionKey> PROCESS_DEFINITION_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionKey.class);
  public static final ObjectMapperSerde<ScheduledKey> SCHEDULE_KEY_SERDE =
      new ObjectMapperSerde<>(ScheduledKey.class);
  public static final ObjectMapperSerde<MessageEventKey> MESSAGE_EVENT_KEY_SERDE =
      new ObjectMapperSerde<>(MessageEventKey.class);
  public static final Serde<UUID> PROCESS_INSTANCE_KEY_SERDE = Serdes.UUID();
  public static final Serde<String> FLOW_NODE_INSTANCE_KEY_SERDE = Serdes.String();
  public static final ObjectMapperSerde<MessageScheduler> MESSAGE_SCHEDULER_SERDE =
      new ObjectMapperSerde<>(MessageScheduler.class);
  public static final ObjectMapperSerde<ProcessInstanceTrigger> PROCESS_INSTANCE_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceTrigger.class);
  public static final ObjectMapperSerde<ProcessDefinitionDTO> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionDTO.class);
  public static final ObjectMapperSerde<JsonNode> VARIABLES_SERDE =
      new ObjectMapperSerde<>(JsonNode.class);
  public static final ObjectMapperSerde<ProcessDefinitionActivation> PROCESS_ACTIVATION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionActivation.class);
  public static final ObjectMapperSerde<DefinitionsDTO> DEFINITIONS_SERDE =
      new ObjectMapperSerde<>(DefinitionsDTO.class);
  public static final ObjectMapperSerde<DefinitionsTrigger> DEFINITIONS_TRIGGER_SERDE =
      new ObjectMapperSerde<>(DefinitionsTrigger.class);
  public static final ObjectMapperSerde<ProcessInstanceDTO> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceDTO.class);
  public static final ObjectMapperSerde<InstanceUpdate> INSTANCE_UPDATE_SERDE =
      new ObjectMapperSerde<>(InstanceUpdate.class);
  public static final ObjectMapperSerde<FlowNodeInstanceDTO> FLOW_NODE_INSTANCE_SERDE =
      new ObjectMapperSerde<>(FlowNodeInstanceDTO.class);
  public static final ObjectMapperSerde<ExternalTaskTrigger> EXTERNAL_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ExternalTaskTrigger.class);
  public static final ObjectMapperSerde<StartCommand> START_COMMAND_SERDE =
      new ObjectMapperSerde<>(StartCommand.class);

  private final MessageSchedulerFactory messageSchedulerFactory;
  private final Clock clock;
  private final KeyValueStoreSupplier keyValueStoreSupplier;
  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final VariablesMapper variablesMapper;
  private final Forwarder forwarder;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    setupNewDefinitionStream(builder);

    setupActivationStream(builder);

    setupMessageStream(builder);

    setupScheduleCommandStream(builder);

    setupProcessInstanceStream(builder);

    return builder.build();
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
            keyValueStoreSupplier.get(Stores.PROCESS_INSTANCE_DEFINITION),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_DEFINITION_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.VARIABLES), Serdes.String(), VARIABLES_SERDE));

    KStream<Object, Object>[] branches =
        builder.stream(
                tenantNamespaceNameWrapper.getPrefixed(
                    Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))
            .process(
                () ->
                    new ProcessInstanceProcessor(
                        definitionMapper,
                        instanceMapper,
                        variablesMapper,
                        forwarder,
                        tenantNamespaceNameWrapper,
                        flowNodeInstancesProcessor),
                tenantNamespaceNameWrapper.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()),
                tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
                tenantNamespaceNameWrapper.getPrefixed(
                    Stores.PROCESS_INSTANCE_DEFINITION.getStorename()),
                tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()))
            .branch(
                (key, value) -> value instanceof ProcessInstanceTrigger,
                (key, value) -> value instanceof InstanceUpdate,
                (key, value) -> value instanceof ExternalTaskTrigger,
                (key, value) -> value instanceof StartCommand,
                (key, value) -> value instanceof MessageScheduler,
                (key, value) -> key instanceof ScheduledKey,
                (key, value) -> value instanceof MessageEvent);

    branches[0]
        .map((key, value) -> KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE));
    branches[1]
        .map((key, value) -> KeyValue.pair((UUID) key, (InstanceUpdate) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.INSTANCE_UPDATE_TOPIC.getTopicName()),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, INSTANCE_UPDATE_SERDE));
    branches[2]
        .map((key, value) -> KeyValue.pair((UUID) key, (ExternalTaskTrigger) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE));
    branches[3]
        .map(
            (key, value) ->
                KeyValue.pair(
                    ((StartCommand) value).getProcessDefinitionId(), (StartCommand) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.PROCESS_DEFINITIONS_TOPIC.getTopicName()),
            Produced.with(Serdes.String(), START_COMMAND_SERDE));
    branches[4]
        .map((key, value) -> KeyValue.pair(((ScheduledKey) key), (MessageScheduler) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    branches[5]
        .map((key, value) -> KeyValue.pair(((ScheduledKey) key), (MessageScheduler) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    branches[6]
        .map((key, value) -> KeyValue.pair(((MessageEventKey) key), (MessageEvent) value))
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE));
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
            () -> new MessageEventProcessor(tenantNamespaceNameWrapper),
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()))
        .split()
        .branch(
            (key, value) -> value instanceof StartCommand,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommand) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINITIONS_TOPIC.getTopicName()),
                            Produced.with(Serdes.String(), START_COMMAND_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupNewDefinitionStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.XML_BY_HASH), Serdes.String(), DEFINITIONS_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.DEFINITION_COUNT_BY_ID),
            Serdes.String(),
            Serdes.Integer()));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(Stores.PROCESS_DEFINITION),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_DEFINITION_SERDE));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.XML_TOPIC.getTopicName()),
            Consumed.with(Serdes.String(), Serdes.String()))
        .map(new DefinitionsMapper())
        .to(
            tenantNamespaceNameWrapper.getPrefixed(Topics.PROCESS_DEFINITIONS_TOPIC.getTopicName()),
            Produced.with(Serdes.String(), DEFINITIONS_SERDE));

    builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.PROCESS_DEFINITIONS_TOPIC.getTopicName()),
            Consumed.with(Serdes.String(), DEFINITIONS_TRIGGER_SERDE))
        .process(
            () -> new DefinitionsProcessor(tenantNamespaceNameWrapper),
            tenantNamespaceNameWrapper.getPrefixed(Stores.XML_BY_HASH.getStorename()),
            tenantNamespaceNameWrapper.getPrefixed(Stores.DEFINITION_COUNT_BY_ID.getStorename()),
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
                                Topics.PROCESS_DEFINITION_PARSED_TOPIC.getTopicName()),
                            Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_DEFINITION_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessDefinitionActivation,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair(
                                    (ProcessDefinitionKey) key,
                                    (ProcessDefinitionActivation) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName()),
                            Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
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
                keyValueStoreSupplier.get(Stores.SCHEDULES),
                SCHEDULE_KEY_SERDE,
                MESSAGE_SCHEDULER_SERDE));

    KStream<ScheduledKey, MessageScheduler> scheduleCommandStream =
        stateStore.stream(
            tenantNamespaceNameWrapper.getPrefixed(Topics.SCHEDULE_COMMANDS.getTopicName()),
            Consumed.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    KStream<Object, SchedulableMessage> processStream =
        scheduleCommandStream.process(
            () -> new ScheduleProcessor(clock, tenantNamespaceNameWrapper),
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES.getStorename()));
    processStream
        .split()
        .branch(
            (k, v) -> v instanceof StartCommand,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommand) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_DEFINITIONS_TOPIC.getTopicName()),
                            Produced.with(Serdes.String(), START_COMMAND_SERDE))))
        .branch(
            (k, v) -> v instanceof ExternalTaskTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((UUID) key, (ExternalTaskTrigger) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE))))
        .branch(
            (k, v) -> v instanceof ProcessInstanceTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName()),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupActivationStream(StreamsBuilder builder) {
    KStream<ProcessDefinitionKey, ProcessDefinitionActivation> activationStreamIn =
        builder.stream(
            tenantNamespaceNameWrapper.getPrefixed(
                Topics.PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName()),
            Consumed.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));

    KStream<Object, Object> scheduleStream =
        activationStreamIn.process(
            () -> new ProcessDefinitionActivationProcessor(messageSchedulerFactory));
    scheduleStream
        .split()
        .branch(
            (key, value) -> value instanceof MessageScheduler,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((ScheduledKey) key, (MessageScheduler) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.SCHEDULE_COMMANDS.getTopicName()),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE))))
        .branch(
            (key, value) -> value == null || value instanceof MessageEvent,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKey) key, (MessageEvent) value))
                        .to(
                            tenantNamespaceNameWrapper.getPrefixed(
                                Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
                            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))));
  }
}
