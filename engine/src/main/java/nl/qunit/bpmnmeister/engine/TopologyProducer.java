package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import nl.qunit.bpmnmeister.model.ProcessDefinition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

@ApplicationScoped
public class TopologyProducer {

    private static final String PROCESS_INSTANCE_TOPIC = "process-instances";
    private static final String PROCESS_DEFINITION_STORE_NAME = "process-definition-store";
    public static final String PROCESS_DEFINITION_TOPIC = "process-definition-input-topic";
    private static final String PROCESS_DEFINITION_OUTPUT_TOPIC = "process-definition-output-topic";

    @Produces
    public Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // Define the state store
        StoreBuilder storeBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(PROCESS_DEFINITION_STORE_NAME),
                Serdes.String(), new ObjectMapperSerde<>(ProcessDefinition.class)
        );

        // Add the state store to the builder
        builder.addStateStore(storeBuilder);

        // Consume messages of type ProcessDefinition from a Kafka topic
        KStream<String, String> processDefinitionStream = builder.stream(PROCESS_DEFINITION_TOPIC);

        // Process the messages and store them in the state store
        processDefinitionStream.foreach((key, value) ->
                builder.table(PROCESS_DEFINITION_OUTPUT_TOPIC));


        return builder.build();
    }
}