package nl.qunit.bpmnmeister.engine;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import nl.qunit.bpmnmeister.model.processdefinition.ProcessDefinition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

@ApplicationScoped
public class TopologyProducer {

  private static final String PROCESS_DEFINITION_STORE = "process-definition-store";
  public static final String PROCESS_DEFINITION_TOPIC = "process-definition-input-topic";
  private static final String PROCESS_DEFINITION_OUTPUT_TOPIC = "process-definition-output-topic";

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();
    ObjectMapperSerde<ProcessDefinition> processDefinitionObjectMapperSerde =
        new ObjectMapperSerde<>(ProcessDefinition.class);

    KeyValueBytesStoreSupplier storeSupplier =
        Stores.persistentKeyValueStore(PROCESS_DEFINITION_STORE);

    GlobalKTable<String, ProcessDefinition> processDefinitions =
        builder.globalTable(
            PROCESS_DEFINITION_TOPIC,
            Consumed.with(Serdes.String(), processDefinitionObjectMapperSerde));

    // Define the state store
    StoreBuilder storeBuilder =
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(PROCESS_DEFINITION_STORE),
            Serdes.String(),
            processDefinitionObjectMapperSerde);

    // Add the state store to the builder
    builder.addStateStore(storeBuilder);

    // Consume messages of type ProcessDefinition from a Kafka topic
    KStream<String, ProcessDefinition> processDefinitionStream =
        builder.stream(PROCESS_DEFINITION_TOPIC);

    // Process the messages and store them in the state store
    processDefinitionStream.foreach(
        (key, value) -> {
          System.out.println("Received process definition: " + value);
        });

    return builder.build();
  }
}
