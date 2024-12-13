package com.flomaestro.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.engine.generic.RestObjectMapper;
import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pd.Stores;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/flownode-instances")
@Slf4j
public class FlowNodeInstanceResource {

  @Inject KafkaStreams kafkaStreams;

  @Inject Client client;
  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  @Inject @RestObjectMapper ObjectMapper restObjectMapper;

  @GET
  @Path("/{flowNodeInstancesId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getFlowNodeInstances(@PathParam("flowNodeInstancesId") UUID flowNodeInstancesId) {

    String envHost = System.getenv("injectedhost");
    int envPort = Integer.parseInt(System.getenv("injectedport"));

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            tenantNamespaceNameWrapper.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()),
            flowNodeInstancesId,
            Serdes.UUID().serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      log.info("no metadata available for key {}", flowNodeInstancesId);
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(envHost)
        && metadata.activeHost().port() == envPort) {
      log.info(
          "host and port match {}:{} ", metadata.activeHost().host(), metadata.activeHost().port());

      ReadOnlyKeyValueStore<String, FlowNodeInstanceDTO> flowNodeInstanceStore =
          getFlowNodeInstanceStore();
      Map<String, FlowNodeInstanceDTO> flowNodeInstances = new HashMap<>();
      flowNodeInstanceStore
          .range(flowNodeInstancesId + ":", flowNodeInstancesId + ":\u00ff")
          .forEachRemaining(
              e -> {
                flowNodeInstances.put(e.key, e.value);
              });
      return Response.ok(flowNodeInstances).build();
    } else {
      log.info(
          "Host and port differ from injected {}:{}, redirecting to host: {} port: {}",
          envHost,
          envPort,
          metadata.activeHost().host(),
          metadata.activeHost().port());

      URI uri =
          getOtherUriForFlowNodeInstances(
              metadata.activeHost().host(), metadata.activeHost().port(), flowNodeInstancesId);
      WebTarget target = client.target(uri);
      Response response = target.request().get();
      return Response.status(response.getStatus()).entity(response.getEntity()).build();
    }
  }

  private ReadOnlyKeyValueStore<String, FlowNodeInstanceDTO> getFlowNodeInstanceStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<String, FlowNodeInstanceDTO>>
            storeQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    tenantNamespaceNameWrapper.getPrefixed(
                        Stores.FLOW_NODE_INSTANCE.getStorename()),
                    QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(storeQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  private URI getOtherUriForFlowNodeInstances(String host, int port, UUID id) {
    try {
      return new URI("http://" + host + ":" + port + "/flownode-instances/" + id);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
