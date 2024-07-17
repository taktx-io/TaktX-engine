package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.Topics.DEFINITIONS_TOPIC;
import static nl.qunit.bpmnmeister.Topics.EXTERNAL_TASK_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.MESSAGE_EVENT_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_DEFINITION_PARSED_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_DEFINTIION_ACTIVATION_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_UPDATE_TOPIC;
import static nl.qunit.bpmnmeister.Topics.SCHEDULE_COMMANDS;
import static nl.qunit.bpmnmeister.Topics.XML_TOPIC;
import static nl.qunit.bpmnmeister.engine.pd.Stores.CHILD_PARENT_PROCESS_INSTANCE_KEY_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.CORRELATION_MESSAGE_SUBSCRIPTION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.DEFINITION_COUNT_BY_ID_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.DEFINITION_MESSAGE_SUBSCRIPTION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_DEFINITION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_INSTANCE_DEFINITION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_INSTANCE_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.SCHEDULES_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.VARIABLES_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.XML_BY_HASH_STORE_NAME;
import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.VariablesParentPair;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKeyElementPair;
import nl.qunit.bpmnmeister.pi.ProcessInstanceMigrationTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
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
public class ProcessDefinitionTopologyProducer {
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
  public static final ObjectMapperSerde<ScheduleKey> SCHEDULE_KEY_SERDE =
      new ObjectMapperSerde<>(ScheduleKey.class);
  public static final ObjectMapperSerde<MessageEventKey> MESSAGE_EVENT_KEY_SERDE =
      new ObjectMapperSerde<>(MessageEventKey.class);
  public static final Serde<UUID> PROCESS_INSTANCE_KEY_SERDE = Serdes.UUID();
  public static final ObjectMapperSerde<ProcessInstanceKeyElementPair>
      PROCESS_INSTANCE_KEY_ELEMENT_PAIR_SERDE =
          new ObjectMapperSerde<>(ProcessInstanceKeyElementPair.class);
  public static final ObjectMapperSerde<MessageScheduler> MESSAGE_SCHEDULER_SERDE =
      new ObjectMapperSerde<>(MessageScheduler.class);
  public static final ObjectMapperSerde<ProcessInstanceTrigger> PROCESS_INSTANCE_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceTrigger.class);
  public static final ObjectMapperSerde<ProcessInstanceMigrationTrigger>
      PROCESS_INSTANCE_MIGRATION_SERDE =
          new ObjectMapperSerde<>(ProcessInstanceMigrationTrigger.class);
  public static final ObjectMapperSerde<ProcessDefinition> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinition.class);
  public static final ObjectMapperSerde<ProcessDefinitionActivation> PROCESS_ACTIVATION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionActivation.class);
  public static final ObjectMapperSerde<Definitions> DEFINITIONS_SERDE =
      new ObjectMapperSerde<>(Definitions.class);
  public static final ObjectMapperSerde<DefinitionsTrigger> DEFINITIONS_TRIGGER_SERDE =
      new ObjectMapperSerde<>(DefinitionsTrigger.class);
  public static final ObjectMapperSerde<ProcessInstance> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstance.class);
  public static final ObjectMapperSerde<ProcessInstanceUpdate> PROCESS_INSTANCE_UPDATE_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceUpdate.class);
  public static final ObjectMapperSerde<ExternalTaskTrigger> EXTERNAL_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ExternalTaskTrigger.class);
  public static final ObjectMapperSerde<StartCommand> START_COMMAND_SERDE =
      new ObjectMapperSerde<>(StartCommand.class);
  public static final ObjectMapperSerde<VariablesParentPair> VARIABLES_PARENT_PAIR_SERDE =
      new ObjectMapperSerde<>(VariablesParentPair.class);

  @Inject MessageSchedulerFactory messageSchedulerFactory;
  @Inject Clock clock;
  @Inject ProcessorProvider processorProvider;
  @Inject KeyValueStoreSupplier keyValueStoreSupplier;

  @Inject
  @CacheName("process-instance-cache")
  Cache processInstanceCache;

  @Inject
  @CacheName("process-instance-definition-cache")
  Cache processInstanceDefinitionCache;

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

  private void setupMessageStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(DEFINITION_MESSAGE_SUBSCRIPTION_STORE_NAME),
            MESSAGE_EVENT_KEY_SERDE,
            DEFINITION_SUBSCRIPTIONS_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(CORRELATION_MESSAGE_SUBSCRIPTION_STORE_NAME),
            MESSAGE_EVENT_KEY_SERDE,
            CORRELATION_SUBSCRIPTIONS_SERDE));

    builder.stream(
            MESSAGE_EVENT_TOPIC.getTopicName(),
            Consumed.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))
        .process(
            MessageEventProcessor::new,
            DEFINITION_MESSAGE_SUBSCRIPTION_STORE_NAME,
            CORRELATION_MESSAGE_SUBSCRIPTION_STORE_NAME)
        .split()
        .branch(
            (key, value) -> value instanceof StartCommand,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommand) value))
                        .to(
                            DEFINITIONS_TOPIC.getTopicName(),
                            Produced.with(Serdes.String(), START_COMMAND_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
                        .to(
                            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupNewDefinitionStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(XML_BY_HASH_STORE_NAME), Serdes.String(), DEFINITIONS_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(DEFINITION_COUNT_BY_ID_STORE_NAME),
            Serdes.String(),
            Serdes.Integer()));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(PROCESS_DEFINITION_STORE_NAME),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_DEFINITION_SERDE));

    builder.stream(XML_TOPIC.getTopicName(), Consumed.with(Serdes.String(), Serdes.String()))
        .map(new DefinitionsMapper())
        .to(DEFINITIONS_TOPIC.getTopicName(), Produced.with(Serdes.String(), DEFINITIONS_SERDE));

    builder.stream(
            DEFINITIONS_TOPIC.getTopicName(),
            Consumed.with(Serdes.String(), DEFINITIONS_TRIGGER_SERDE))
        .process(
            DefinitionsProcessor::new,
            XML_BY_HASH_STORE_NAME,
            DEFINITION_COUNT_BY_ID_STORE_NAME,
            PROCESS_DEFINITION_STORE_NAME)
        .split()
        .branch(
            (key, value) -> value instanceof ProcessDefinition,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair(
                                    (ProcessDefinitionKey) key, (ProcessDefinition) value))
                        .to(
                            PROCESS_DEFINITION_PARSED_TOPIC.getTopicName(),
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
                            PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName(),
                            Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE))))
        .branch(
            (key, value) -> value instanceof ProcessInstanceTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
                        .to(
                            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupProcessInstanceStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(PROCESS_INSTANCE_STORE_NAME),
            PROCESS_INSTANCE_KEY_SERDE,
            PROCESS_INSTANCE_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(PROCESS_INSTANCE_DEFINITION_STORE_NAME),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_DEFINITION_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(CHILD_PARENT_PROCESS_INSTANCE_KEY_STORE_NAME),
            PROCESS_INSTANCE_KEY_SERDE,
            PROCESS_INSTANCE_KEY_ELEMENT_PAIR_SERDE));
    builder.addStateStore(
        keyValueStoreBuilder(
            keyValueStoreSupplier.get(VARIABLES_STORE_NAME),
            PROCESS_INSTANCE_KEY_SERDE,
            VARIABLES_PARENT_PAIR_SERDE));

    KStream<Object, Object>[] branches =
        builder.stream(
                PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))
            .process(
                () ->
                    new ProcessInstanceProcessor(
                        processorProvider, processInstanceCache, processInstanceDefinitionCache),
                PROCESS_INSTANCE_STORE_NAME,
                PROCESS_INSTANCE_DEFINITION_STORE_NAME,
                CHILD_PARENT_PROCESS_INSTANCE_KEY_STORE_NAME,
                VARIABLES_STORE_NAME)
            .branch(
                (key, value) -> value instanceof ProcessInstanceUpdate,
                (key, value) -> value instanceof ProcessInstanceTrigger,
                (key, value) -> value instanceof ExternalTaskTrigger,
                (key, value) -> value instanceof StartCommand,
                (key, value) -> value instanceof MessageScheduler,
                (key, value) -> key instanceof ScheduleKey,
                (key, value) -> value instanceof MessageEvent);

    branches[0]
        .map((key, value) -> KeyValue.pair((UUID) key, (ProcessInstanceUpdate) value))
        .to(
            PROCESS_INSTANCE_UPDATE_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_UPDATE_SERDE));
    branches[1]
        .map((key, value) -> KeyValue.pair((UUID) key, (ProcessInstanceTrigger) value))
        .to(
            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE));
    branches[2]
        .map((key, value) -> KeyValue.pair((UUID) key, (ExternalTaskTrigger) value))
        .to(
            EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE));
    branches[3]
        .map(
            (key, value) ->
                KeyValue.pair(
                    ((StartCommand) value).getProcessDefinitionId(), (StartCommand) value))
        .to(DEFINITIONS_TOPIC.getTopicName(), Produced.with(Serdes.String(), START_COMMAND_SERDE));
    branches[4]
        .map((key, value) -> KeyValue.pair(((ScheduleKey) key), (MessageScheduler) value))
        .to(
            SCHEDULE_COMMANDS.getTopicName(),
            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    branches[5]
        .map((key, value) -> KeyValue.pair(((ScheduleKey) key), (MessageScheduler) value))
        .to(
            SCHEDULE_COMMANDS.getTopicName(),
            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    branches[6]
        .map((key, value) -> KeyValue.pair(((MessageEventKey) key), (MessageEvent) value))
        .to(
            MESSAGE_EVENT_TOPIC.getTopicName(),
            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE));
  }

  private void setupScheduleCommandStream(StreamsBuilder builder) {
    StreamsBuilder stateStore =
        builder.addStateStore(
            keyValueStoreBuilder(
                keyValueStoreSupplier.get(SCHEDULES_STORE_NAME),
                SCHEDULE_KEY_SERDE,
                MESSAGE_SCHEDULER_SERDE));

    KStream<ScheduleKey, MessageScheduler> scheduleCommandStream =
        stateStore.stream(
            Topics.SCHEDULE_COMMANDS.getTopicName(),
            Consumed.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE));
    KStream<Object, SchedulableMessage> processStream =
        scheduleCommandStream.process(() -> new ScheduleProcessor(clock), SCHEDULES_STORE_NAME);
    processStream
        .split()
        .branch(
            (k, v) -> v instanceof StartCommand,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((String) key, (StartCommand) value))
                        .to(
                            DEFINITIONS_TOPIC.getTopicName(),
                            Produced.with(Serdes.String(), START_COMMAND_SERDE))))
        .branch(
            (k, v) -> v instanceof ExternalTaskTrigger,
            Branched.withConsumer(
                ks ->
                    ks.map((key, value) -> KeyValue.pair((UUID) key, (ExternalTaskTrigger) value))
                        .to(
                            EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
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
                            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupActivationStream(StreamsBuilder builder) {
    KStream<ProcessDefinitionKey, ProcessDefinitionActivation> activationStreamIn =
        builder.stream(
            PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName(),
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
                                KeyValue.pair((ScheduleKey) key, (MessageScheduler) value))
                        .to(
                            Topics.SCHEDULE_COMMANDS.getTopicName(),
                            Produced.with(SCHEDULE_KEY_SERDE, MESSAGE_SCHEDULER_SERDE))))
        .branch(
            (key, value) -> value == null || value instanceof MessageEvent,
            Branched.withConsumer(
                ks ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair((MessageEventKey) key, (MessageEvent) value))
                        .to(
                            MESSAGE_EVENT_TOPIC.getTopicName(),
                            Produced.with(MESSAGE_EVENT_KEY_SERDE, MESSAGE_EVENT_SERDE))));
  }
}
