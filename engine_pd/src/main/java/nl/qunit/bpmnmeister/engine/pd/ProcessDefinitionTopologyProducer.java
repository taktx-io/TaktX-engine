package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.engine.Topics.*;
import static nl.qunit.bpmnmeister.engine.pd.Stores.*;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.util.GenerationExtractor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;

@ApplicationScoped
public class ProcessDefinitionTopologyProducer {

  @Inject BpmnParser bpmnParser;
  @Inject GenerationExtractor generationExtractor;

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();
    Initializer<ProcessDefinition> initializer = () -> new ProcessDefinition(null, 0);

    Aggregator<String, Definitions, ProcessDefinition> aggregator =
        (key, value, aggregation) -> new ProcessDefinition(value, aggregation.getVersion() + 1);

    ObjectMapperSerde<ProcessDefinitionKey> processDefinitionKeySerde =
        new ObjectMapperSerde<>(ProcessDefinitionKey.class);
    ObjectMapperSerde<ProcessDefinition> processDefinitionSerde =
        new ObjectMapperSerde<>(ProcessDefinition.class);
    ObjectMapperSerde<ProcessDefinitionStateWrapper> processDefinitionStateSerde =
        new ObjectMapperSerde<>(ProcessDefinitionStateWrapper.class);

    builder.addStateStore(
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(UNIQUE_KEY_DEFINITIONS_STORE_NAME),
            Serdes.String(),
            new ObjectMapperSerde<>(Definitions.class)));

    builder.addStateStore(
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(PD_STATE_STORE_NAME),
            processDefinitionKeySerde,
            processDefinitionStateSerde));

    KStream<String, String> xmlStream =
        builder.stream(XML_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

    KStream<ProcessDefinitionKey, ProcessDefinition> definitionStream =
        xmlStream
            .map(new UniqueXmlKeyMapper(generationExtractor, bpmnParser))
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
                        Stores.persistentKeyValueStore(PROCESS_DEFINITION_PARSED_STORE))
                    .withKeySerde(Serdes.String())
                    .withValueSerde(processDefinitionSerde))
            .toStream()
            .map(
                (key, value) ->
                    KeyValue.pair(
                        new ProcessDefinitionKey(
                            value.getDefinitions().getProcessDefinitionId(),
                            value.getDefinitions().getGeneration(),
                            value.getVersion()),
                        value));

    definitionStream.to(
        PROCESS_DEFINITION_PARSED_TOPIC,
        Produced.with(processDefinitionKeySerde, processDefinitionSerde));

    definitionStream
        .process(ProcessDefinitionStateProcessor::new, PD_STATE_STORE_NAME)
        .to(
            PROCESS_ACTIVATION_TOPIC,
            Produced.with(
                processDefinitionKeySerde, new ObjectMapperSerde<>(ProcessActivation.class)));

    return builder.build();
  }
}
