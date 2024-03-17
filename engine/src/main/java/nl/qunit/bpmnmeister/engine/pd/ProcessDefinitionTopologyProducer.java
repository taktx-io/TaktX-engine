package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.Topics.EXTERNAL_TASK_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_DEFINTIION_ACTIVATION_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_MIGRATION_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_START_COMMAND_TOPIC;
import static nl.qunit.bpmnmeister.Topics.PROCESS_INSTANCE_TRIGGER_TOPIC;
import static nl.qunit.bpmnmeister.Topics.XML_TOPIC;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_DEFINITION_ACTIVATION_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_DEFINITION_PARSED_STORE;
import static nl.qunit.bpmnmeister.engine.pd.Stores.PROCESS_INSTANCE_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.SCHEDULES_STORE_NAME;
import static nl.qunit.bpmnmeister.engine.pd.Stores.UNIQUE_KEY_DEFINITIONS_STORE_NAME;
import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;
import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

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
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceMigrationTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleStartCommand;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Initializer;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
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
  static final ObjectMapperSerde<Trigger> TRIGGER_SERDE = new ObjectMapperSerde<>(Trigger.class);
  static final ObjectMapperSerde<ProcessInstanceMigrationTrigger> PROCESS_INSTANCE_MIGRATION_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceMigrationTrigger.class);
  static final ObjectMapperSerde<ProcessDefinition> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinition.class);
  static final ObjectMapperSerde<ProcessDefinitionActivation> PROCESS_ACTIVATION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionActivation.class);
  public static final ObjectMapperSerde<Definitions> DEFINITIONS_SERDE =
      new ObjectMapperSerde<>(Definitions.class);
  private static final ObjectMapperSerde<ProcessInstance> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstance.class);
  private static final ObjectMapperSerde<ExternalTaskTrigger> EXTERNAL_TASK_TRIGGER_SERDE =
      new ObjectMapperSerde<>(ExternalTaskTrigger.class);
  private static final ObjectMapperSerde<ProcessInstanceStartCommand>
      PROCESS_INSTANCE_START_COMMAND_SERDE =
          new ObjectMapperSerde<>(ProcessInstanceStartCommand.class);

  @Inject ScheduleCommandFactory scheduleCommandFactory;
  @Inject Clock clock;
  @Inject ProcessorProvider processorProvider;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    setupDefinitionStream(builder);

    setupActivationStream(builder);

    setupStartScheduleCommandStream(builder);

    setupProcessInstanceStartCommandStream(builder);

    setupProcessInstanceStream(builder);

    setupProcessInstanceMigrationStream(builder);

    return builder.build();
  }

  private void setupProcessInstanceStartCommandStream(StreamsBuilder builder) {
    builder.stream(
            PROCESS_INSTANCE_START_COMMAND_TOPIC.getTopicName(),
            Consumed.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_INSTANCE_START_COMMAND_SERDE))
        .process(
            ProcessInstanceStartCommandProcessor::new, PROCESS_DEFINITION_ACTIVATION_STORE_NAME)
        .to(
            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, TRIGGER_SERDE));
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
            persistentKeyValueStore(PROCESS_INSTANCE_STORE_NAME),
            PROCESS_INSTANCE_KEY_SERDE,
            PROCESS_INSTANCE_SERDE));

    KStream<Object, Object>[] branches =
        builder.stream(
                PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                Consumed.with(PROCESS_INSTANCE_KEY_SERDE, TRIGGER_SERDE))
            .process(
                () -> new ProcessInstanceProcessor(processorProvider), PROCESS_INSTANCE_STORE_NAME)
            .branch(
                (key, value) -> value instanceof FlowElementTrigger,
                (key, value) -> value instanceof ExternalTaskTrigger);

    branches[0]
        .map((key, value) -> KeyValue.pair((ProcessInstanceKey) key, (Trigger) value))
        .to(
            PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, TRIGGER_SERDE));
    branches[1]
        .map((key, value) -> KeyValue.pair((ProcessInstanceKey) key, (ExternalTaskTrigger) value))
        .to(
            EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
            Produced.with(PROCESS_INSTANCE_KEY_SERDE, EXTERNAL_TASK_TRIGGER_SERDE));
  }

  private void setupStartScheduleCommandStream(StreamsBuilder builder) {
    StreamsBuilder stateStore =
        builder.addStateStore(
            keyValueStoreBuilder(
                persistentKeyValueStore(SCHEDULES_STORE_NAME),
                SCHEDULE_KEY_SERDE,
                SCHEDULE_COMMAND_SERDE));

    KStream<ScheduleKey, ScheduleStartCommand> scheduleCommandStream =
        stateStore.stream(
            Topics.SCHEDULE_COMMANDS.getTopicName(),
            Consumed.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
    KStream<ProcessDefinitionKey, ProcessInstanceStartCommand> processStream =
        scheduleCommandStream.process(() -> new ScheduleProcessor(clock), SCHEDULES_STORE_NAME);
    processStream.to(
        PROCESS_INSTANCE_START_COMMAND_TOPIC.getTopicName(),
        Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_INSTANCE_START_COMMAND_SERDE));
  }

  private void setupDefinitionStream(StreamsBuilder builder) {
    Initializer<ProcessDefinition> initializer = () -> new ProcessDefinition(Definitions.NONE, 0);

    Aggregator<String, Definitions, ProcessDefinition> aggregator =
        (key, value, aggregation) -> new ProcessDefinition(value, aggregation.getVersion() + 1);

    builder.addStateStore(
        keyValueStoreBuilder(
            persistentKeyValueStore(UNIQUE_KEY_DEFINITIONS_STORE_NAME),
            Serdes.String(),
            DEFINITIONS_SERDE));

    KStream<String, String> xmlStream =
        builder.stream(XML_TOPIC.getTopicName(), Consumed.with(Serdes.String(), Serdes.String()));

    KStream<String, Definitions> generatedKeyDefinitionStream =
        xmlStream.map(new UniqueXmlKeyMapper());

    KStream<ProcessDefinitionKey, ProcessDefinition> processDefinitionStream =
        generatedKeyDefinitionStream
            .process(StoreDefinitionsProcessor::new, UNIQUE_KEY_DEFINITIONS_STORE_NAME)
            .map(
                (key, value) ->
                    KeyValue.pair(
                        value.getProcessDefinitionId() + "." + value.getGeneration(), value))
            .groupByKey(Grouped.with(Serdes.String(), new ObjectMapperSerde<>(Definitions.class)))
            .aggregate(
                initializer,
                aggregator,
                Materialized.<String, ProcessDefinition>as(
                        persistentKeyValueStore(PROCESS_DEFINITION_PARSED_STORE))
                    .withKeySerde(Serdes.String())
                    .withValueSerde(PROCESS_DEFINITION_SERDE))
            .toStream()
            .map(
                (key, value) ->
                    KeyValue.pair(
                        new ProcessDefinitionKey(
                            value.getDefinitions().getProcessDefinitionId(),
                            value.getDefinitions().getGeneration(),
                            value.getVersion()),
                        value));
    processDefinitionStream.to(
        Topics.PROCESS_DEFINITION_PARSED_TOPIC.getTopicName(),
        Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_DEFINITION_SERDE));

    KStream<ProcessDefinitionKey, ProcessDefinitionActivation> activationStreamOut =
        processDefinitionStream.process(ProcessDefinitionActivationProcessor::new);

    activationStreamOut.to(
        PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName(),
        Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));
  }

  private void setupActivationStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            persistentKeyValueStore(PROCESS_DEFINITION_ACTIVATION_STORE_NAME),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_ACTIVATION_SERDE));

    KStream<ProcessDefinitionKey, ProcessDefinitionActivation> activationStreamIn =
        builder.stream(
            PROCESS_DEFINTIION_ACTIVATION_TOPIC.getTopicName(),
            Consumed.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));

    KStream<ScheduleKey, ScheduleStartCommand> scheduleStream =
        activationStreamIn.process(
            () -> new StoreProcessDefinitionActivationProcessor(scheduleCommandFactory),
            PROCESS_DEFINITION_ACTIVATION_STORE_NAME);

    scheduleStream.to(
        Topics.SCHEDULE_COMMANDS.getTopicName(),
        Produced.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
  }
}
