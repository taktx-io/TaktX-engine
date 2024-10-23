package nl.qunit.bpmnmeister.engine.api;

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
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-instances")
public class ProcessInstanceResource {

  @Inject KafkaStreams kafkaStreams;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<UUID> getProcessInstances() {
    List<UUID> processInstances = new ArrayList<>();
    ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore =
        getProcessInstanceStore();

    Collection<StreamsMetadata> streamsMetadata =
        kafkaStreams.streamsMetadataForStore(Stores.PROCESS_INSTANCE_STORE_NAME);

    streamsMetadata.forEach(
        metadata -> {
          System.out.println("Host: " + metadata.host());
          System.out.println("Port: " + metadata.port());
        });
    processInstanceStore.all().forEachRemaining(record -> processInstances.add(record.key));
    return processInstances;
  }

  private ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO> getProcessInstanceStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO>>
            processInstanceStoreQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    Stores.PROCESS_INSTANCE_STORE_NAME, QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(processInstanceStoreQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  private ReadOnlyKeyValueStore<UUID, VariablesDTO> getVariablesStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<UUID, VariablesDTO>>
            storeQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    Stores.VARIABLES_STORE_NAME, QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(storeQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  @GET
  @Path("/{processId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessInstance(@PathParam("processId") UUID processId)
      throws UnknownHostException {

    String envHost = System.getenv("injectedhost");

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            Stores.PROCESS_INSTANCE_STORE_NAME, processId, Serdes.UUID().serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)) {
      ProcessInstanceDTO processInstanceDTO = getProcessInstanceStore().get(processId);
      if (processInstanceDTO == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        return Response.ok(processInstanceDTO).build();
      }
    } else {
      URI uri = getOtherUri(metadata.activeHost().host(), metadata.activeHost().port(), processId);
      return Response.temporaryRedirect(uri).build();
    }
  }

  @GET
  @Path("/{processId}/variables")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessInstanceVariables(@PathParam("processId") UUID processId)
      throws UnknownHostException {

    String envHost = System.getenv("injectedhost");

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            Stores.VARIABLES_STORE_NAME, processId, Serdes.UUID().serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)) {
      VariablesDTO variables = getVariablesStore().get(processId);
      if (variables == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        return Response.ok(variables).build();
      }
    } else {
      URI uri = getOtherUri(metadata.activeHost().host(), metadata.activeHost().port(), processId);
      return Response.temporaryRedirect(uri).build();
    }
  }

  private URI getOtherUri(String host, int port, UUID id) {
    try {
      return new URI("http://" + host + ":" + port + "/process-instances/" + id);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
