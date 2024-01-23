package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.engine.Topics.*;
import static nl.qunit.bpmnmeister.engine.pd.Stores.*;
import static org.apache.kafka.streams.state.Stores.keyValueStoreBuilder;
import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import nl.qunit.bpmnmeister.engine.Topics;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.ProcessActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.scheduler.*;
import nl.qunit.bpmnmeister.util.GenerationExtractor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

@ApplicationScoped
public class ProcessDefinitionTopologyProducer {
  static final ObjectMapperSerde<ProcessDefinitionKey> PROCESS_DEFINITION_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessDefinitionKey.class);
  static final ObjectMapperSerde<ScheduleKey> SCHEDULE_KEY_SERDE =
      new ObjectMapperSerde<>(ScheduleKey.class);
  static final ObjectMapperSerde<ScheduleCommand> SCHEDULE_COMMAND_SERDE =
      new ObjectMapperSerde<>(ScheduleCommand.class);
  static final ObjectMapperSerde<ProcessDefinition> PROCESS_DEFINITION_SERDE =
      new ObjectMapperSerde<>(ProcessDefinition.class);
  static final ObjectMapperSerde<ProcessActivation> PROCESS_ACTIVATION_SERDE =
      new ObjectMapperSerde<>(ProcessActivation.class);
  public static final ObjectMapperSerde<Definitions> DEFINITIONS_SERDE =
      new ObjectMapperSerde<>(Definitions.class);

  private static final ObjectMapperSerde<ProcessInstanceKey> PROCESS_INSTANCE_KEY_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceKey.class);
  private static final ObjectMapperSerde<ProcessInstanceTrigger> PROCESS_INSTANCE_SERDE =
      new ObjectMapperSerde<>(ProcessInstanceTrigger.class);

  @Inject BpmnParser bpmnParser;
  @Inject GenerationExtractor generationExtractor;
  @Inject ScheduleCommandFactory scheduleCommandFactory;
  @Inject Clock clock;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    setupDefinitionStream(builder);

    setupActivationStream(builder);

    setupScheduleCommandStream(builder);

    Topology topologu = builder.build();

    return topologu;
  }

  private void setupScheduleCommandStream(StreamsBuilder builder) {
    StreamsBuilder stateStore =
        builder.addStateStore(
            keyValueStoreBuilder(
                persistentKeyValueStore(SCHEDULES_STORE_NAME),
                SCHEDULE_KEY_SERDE,
                SCHEDULE_COMMAND_SERDE));

    KStream<ScheduleKey, ScheduleCommand> scheduleCommandStream =
        stateStore.stream(
            Topics.SCHEDULE_COMMANDS, Consumed.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
    KStream<ProcessInstanceKey, ProcessInstanceTrigger> processStream =
        scheduleCommandStream.process(() -> new ScheduleProcessor(clock), SCHEDULES_STORE_NAME);
    processStream.to(
        Topics.TRIGGER_TOPIC, Produced.with(PROCESS_INSTANCE_KEY_SERDE, PROCESS_INSTANCE_SERDE));
  }

  private void setupDefinitionStream(StreamsBuilder builder) {
    Initializer<ProcessDefinition> initializer = () -> new ProcessDefinition(null, 0);

    Aggregator<String, Definitions, ProcessDefinition> aggregator =
        (key, value, aggregation) -> new ProcessDefinition(value, aggregation.getVersion() + 1);

    builder.addStateStore(
        keyValueStoreBuilder(
            persistentKeyValueStore(UNIQUE_KEY_DEFINITIONS_STORE_NAME),
            Serdes.String(),
            DEFINITIONS_SERDE));

    KStream<String, String> xmlStream =
        builder.stream(XML_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

    KStream<String, Definitions> generatedKeyDefinitionStream =
        xmlStream.map(new UniqueXmlKeyMapper(generationExtractor, bpmnParser));

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
        Topics.PROCESS_DEFINITION_PARSED_TOPIC,
        Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_DEFINITION_SERDE));

    KStream<ProcessDefinitionKey, ProcessActivation> activationStreamOut =
        processDefinitionStream.process(ProcessActivationProcessor::new);

    activationStreamOut.to(
        PROCESS_ACTIVATION_TOPIC,
        Produced.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));
  }

  private void setupActivationStream(StreamsBuilder builder) {
    builder.addStateStore(
        keyValueStoreBuilder(
            persistentKeyValueStore(PROCESS_ACTIVATION_STORE_NAME),
            PROCESS_DEFINITION_KEY_SERDE,
            PROCESS_ACTIVATION_SERDE));

    KStream<ProcessDefinitionKey, ProcessActivation> activationStreamIn =
        builder.stream(
            PROCESS_ACTIVATION_TOPIC,
            Consumed.with(PROCESS_DEFINITION_KEY_SERDE, PROCESS_ACTIVATION_SERDE));

    KStream<ScheduleKey, ScheduleCommand> scheduleStream =
        activationStreamIn.process(
            () -> new StoreProcessActivationProcessor(scheduleCommandFactory),
            PROCESS_ACTIVATION_STORE_NAME);

    scheduleStream.to(
        Topics.SCHEDULE_COMMANDS, Produced.with(SCHEDULE_KEY_SERDE, SCHEDULE_COMMAND_SERDE));
  }
}
