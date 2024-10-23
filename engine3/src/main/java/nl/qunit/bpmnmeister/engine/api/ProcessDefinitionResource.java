package nl.qunit.bpmnmeister.engine.api;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.qunit.bpmnmeister.engine.generic.TopologyProducer;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-definitions")
public class ProcessDefinitionResource {
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> store;
  @Inject KafkaStreams kafkaStreams;

  @PostConstruct
  void init() {
    StoreQueryParameters<
            ? extends ReadOnlyKeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO>>
        STORE_QUERY_PARAMETERS =
            StoreQueryParameters.fromNameAndType(
                Stores.PROCESS_DEFINITION_STORE_NAME, QueryableStoreTypes.keyValueStore());
    store = kafkaStreams.store(STORE_QUERY_PARAMETERS);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionKey> getProcessDefinitionKeys() {
    List<ProcessDefinitionKey> processDefinitionKeys = new ArrayList<>();

    Collection<StreamsMetadata> streamsMetadata =
        kafkaStreams.streamsMetadataForStore(Stores.PROCESS_DEFINITION_STORE_NAME);
    streamsMetadata.forEach(
        metadata -> {
          System.out.println("Host: " + metadata.host());
          System.out.println("Port: " + metadata.port());
        });
    store.all().forEachRemaining(record -> processDefinitionKeys.add(record.key));
    return processDefinitionKeys;
  }

  @GET
  @Path("/{processDefinitionKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessDefinition(
      @PathParam("processDefinitionKey") String processDefinitionKeyString)
      throws UnknownHostException {

    String envHost = System.getenv("injectedhost");

    String[] split = processDefinitionKeyString.split("\\.");
    String processDefinitionName = split[0];
    Integer processDefinitionVersion = Integer.parseInt(split[1]);
    ProcessDefinitionKey processDefinitionKey =
        new ProcessDefinitionKey(processDefinitionName, processDefinitionVersion);
    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            Stores.PROCESS_INSTANCE_STORE_NAME,
            processDefinitionKey,
            TopologyProducer.PROCESS_DEFINITION_KEY_SERDE.serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)) {
      ProcessDefinitionDTO processDefinition = store.get(processDefinitionKey);
      if (processDefinition == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        return Response.ok(processDefinition).build();
      }
    } else {
      URI uri =
          getOtherUri(
              metadata.activeHost().host(),
              metadata.activeHost().port(),
              processDefinitionKeyString);
      return Response.temporaryRedirect(uri).build();
    }
  }

  private URI getOtherUri(String host, int port, String id) {
    try {
      return new URI("http://" + host + ":" + port + "/process-definitions/" + id);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
