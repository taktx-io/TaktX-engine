package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.Topics.DEFINITIONS_TOPIC;
import static nl.qunit.bpmnmeister.Topics.EXTERNAL_TASK_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_DEFINITION_PARSED_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_DEFINTIION_ACTIVATION_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_MIGRATION_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.XML_TOPIC;
import static nl.qunit.bpmnmeister.engine.pd.Stores.DEFINITION_COUNT_BY_ID_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_DEFINITION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_INSTANCE_DEFINITION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_INSTANCE_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.SCHEDULES_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.XML_BY_HASH_STORE_NAME;
import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMigrationProcessor;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceMigrationTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleStartCommand;
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
  static final ObjectMapperSerde<ProcessDefinitionKey> PROCESS_DEFINITION_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionKey.class);
  static final ObjectMapperSerde<ScheduleKey> SCHEDULE_KEY_SERDE =
      new ObjectMapperSerde<>(ScheduleKey.class);
  static final ObjectMapperSerde<ProcessInstanceKey> PROCESS_INSTANCE_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceKey.class);
  static final ObjectMapperSerde<ScheduleStartCommand> SCHEDULE_COMMAND_SERDE =
      new ObjectMapperSerde<>(ScheduleStartCommand.class);
  static final ObjectMapperSerde<ProcessInstanceTrigger> PROCESS_INSTANCE_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceTrigger.class);
  static final ObjectMapperSerde<ProcessInstanceMigrationTrigger> PROCESS_INSTANCE_MIGRATION_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceMigrationTrigger.class);
  static final ObjectMapperSerde<ProcessDefinition> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinition.class);
  static final ObjectMapperSerde<ProcessDefinitionActivation> PROCESS_ACTIVATION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionActivation.class);
  public static final ObjectMapperSerde<Definitions> DEFINITIONS_SERDE =
      new ObjectMapperSerde<>(Definitions.class);
  public static final ObjectMapperSerde<DefinitionsTrigger> DEFINITIONS_TRIGGER_SERDE =
      new ObjectMapperSerde<>(DefinitionsTrigger.class);
  private static final ObjectMapperSerde<ProcessInstance> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstance.class);
  private static final ObjectMapperSerde<ExternalTaskTrigger> EXTERNAL_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ExternalTaskTrigger.class);
  private static final ObjectMapperSerde<StartCommand> START_COMMAND_SERDE =
      new ObjectMapperSerde<>(StartCommand.class);

  @Inject ScheduleCommandFactory scheduleCommandFactory;
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

    setupStartScheduleCommandStream(builder);

    setupProcessInstanceStream(builder);

    setupProcessInstanceMigrationStream(builder);

    return builder.build();
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
            Stores.XML_BY_HASH_STORE_NAME,
            DEFINITION_COUNT_BY_ID_STORE_NAME,
            PROCESS_DEFINITION_STORE_NAME)
        .split()
        .branch(
            (key, value) -> value instanceof ProcessDefinition,
            Branched.withConsumer(
                (ks) ->
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
                (ks) ->
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
                (ks) ->
                    ks.map(
                            (key, value) ->
                                KeyValue.pair(
                                    (ProcessInstanceKey) key, (ProcessInstanceTrigger) value))
                        .to(
                            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                            Produced.with(
                                PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))));
  }

  private void setupProcessInstanceMigrationStream(StreamsBuilder builder) {
    builder.stream(
            PROCESS_INSTANCE_MIGRATION_TOPIC.getTopicName(),
            Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_MIGRATION_SERDE))
        .process(
            () -> new ProcessInstanceMigrationProcessor(processorProvider),
            PROCESS_INSTANCE_STORE_NAME);
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

    KStream<Object, Object>[] branches =
        builder.stream(
                PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                Consumed.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE))
            .process(
                () ->
                    new ProcessInstanceProcessor(
                        processorProvider, processInstanceCache, processInstanceDefinitionCache),
                PROCESS_INSTANCE_STORE_NAME,
                PROCESS_INSTANCE_DEFINITION_STORE_NAME)
            .branch(
                (key, value) -> value instanceof ProcessInstance,
                (key, value) -> value instanceof FlowElementTrigger,
                (key, value) -> value instanceof ExternalTaskTrigger,
                (key, value) -> value instanceof StartCommand);

    branches[0]
        .map((key, value) -> KeyValue.pair((ProcessInstanceKey) key, (ProcessInstance) value))
        .to(
            PROCESS_INSTANCE_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_SERDE));
    branches[1]
        .map(
            (key, value) -> KeyValue.pair((ProcessInstanceKey) key, (ProcessInstanceTrigger) value))
        .to(
            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_TRIGGER_SERDE));
    branches[2]
        .map((key, value) -> KeyValue.pair((ProcessInstanceKey) key, (ExternalTaskTrigger) value))
        .to(
            EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE));
    branches[3]
        .map(
            (key, value) ->
                KeyValue.pair(
                    ((StartCommand) value).getProcessDefinitionId(), (StartCommand) value))
        .to(DEFINITIONS_TOPIC.getTopicName(), Produced.with(Serdes.String(), START_COMMAND_SERDE));
  }

  private void setupStartScheduleCommandStream(StreamsBuilder builder) {
    StreamsBuilder stateStore =
        builder.addStateStore(
            keyValueStoreBuilder(
                keyValueStoreSupplier.get(SCHEDULES_STORE_NAME),
                SCHEDULE_KEY_SERDE,
                SCHEDULE_COMMAND_SERDE));

    KStream<ScheduleKey, ScheduleStartCommand> scheduleCommandStream =
        stateStore.stream(
            Topics.SCHEDULE_COMMANDS.getTopicName(),
            Consumed.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
    KStream<String, StartCommand> processStream =
        scheduleCommandStream.process(() -> new ScheduleProcessor(clock), SCHEDULES_STORE_NAME);
    processStream.to(
        DEFINITIONS_TOPIC.getTopicName(), Produced.with(Serdes.String(), START_COMMAND_SERDE));
  }

  private void setupActivationStream(StreamsBuilder builder) {
    KStream<ProcessDefinitionKey, ProcessDefinitionActivation> activationStreamIn =
        builder.stream(
            PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName(),
            Consumed.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));

    KStream<ScheduleKey, ScheduleStartCommand> scheduleStream =
        activationStreamIn.process(
            () -> new ProcessDefinitionActivationProcessor(scheduleCommandFactory));

    scheduleStream.to(
        Topics.SCHEDULE_COMMANDS.getTopicName(),
        Produced.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
  }
}
