package nl.qunit.bpmnmeister.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.pi.state.ProcessInstanceDTO;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-instances")
@Slf4j
public class ProcessInstanceResource {

  @Inject KafkaStreams kafkaStreams;

  @Inject Client client;
  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<UUID> getProcessInstances() {
    List<UUID> processInstances = new ArrayList<>();
    ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore =
        getProcessInstanceStore();

    Collection<StreamsMetadata> streamsMetadata =
        kafkaStreams.streamsMetadataForStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()));

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
                    tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
                    QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(processInstanceStoreQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  private ReadOnlyKeyValueStore<String, JsonNode> getVariablesStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<String, JsonNode>>
            storeQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()),
                    QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(storeQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  @GET
  @Path("/{processId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessInstance(@PathParam("processId") UUID processId) {

    String envHost = System.getenv("injectedhost");
    int envPort = Integer.parseInt(System.getenv("injectedport"));

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
            processId,
            Serdes.UUID().serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      log.info("no metadata available for key {}", processId);
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)
        && metadata.activeHost().port() == envPort) {
      log.info(
          "host and port match {}:{} ", metadata.activeHost().host(), metadata.activeHost().port());
      ProcessInstanceDTO processInstanceDTO = getProcessInstanceStore().get(processId);
      if (processInstanceDTO == null) {
        log.info("processInstanceDTO is null for key {}", processId);
        return Response.status(Response.Status.NOT_FOUND).build();
      } else {
        log.info("processInstanceDTO found for key {}", processId);
        return Response.ok(processInstanceDTO).build();
      }
    } else {

      log.info(
          "Host and port differ from injected {}:{}, redirecting to host: {} port: {}",
          envHost,
          envPort,
          metadata.activeHost().host(),
          metadata.activeHost().port());

      URI uri =
          getOtherUriForProcessInstance(
              metadata.activeHost().host(), metadata.activeHost().port(), processId);
      WebTarget target = client.target(uri);
      Response response = target.request().get();
      return Response.status(response.getStatus()).entity(response.getEntity()).build();
    }
  }

  @GET
  @Path("/{processId}/variables")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessInstanceVariables(@PathParam("processId") UUID processId) {

    String envHost = System.getenv("injectedhost");
    int envPort = Integer.parseInt(System.getenv("injectedport"));

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()),
            processId,
            Serdes.UUID().serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)
        && metadata.activeHost().port() == envPort) {

      Map<String, JsonNode> variables = new HashMap<>();
      getVariablesStore()
          .range(processId + ":", processId + "\u00ff")
          .forEachRemaining(
              entry -> {
                variables.put(entry.key.substring(entry.key.indexOf(":") + 1), entry.value);
              });
      return Response.ok(variables).build();
    } else {
      URI uri =
          getOtherUriForProcessInstanceVariables(
              metadata.activeHost().host(), metadata.activeHost().port(), processId);
      WebTarget target = client.target(uri);
      Response response = target.request().get();
      return Response.status(response.getStatus()).entity(response.getEntity()).build();
    }
  }

  private URI getOtherUriForProcessInstance(String host, int port, UUID id) {
    try {
      return new URI("http://" + host + ":" + port + "/process-instances/" + id);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private URI getOtherUriForProcessInstanceVariables(String host, int port, UUID id) {
    try {
      return new URI("http://" + host + ":" + port + "/process-instances/" + id + "/variables");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
