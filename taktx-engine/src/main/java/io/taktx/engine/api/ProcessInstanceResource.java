/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.Constants;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.util.TaktUUIDSerializer;
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
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-instances")
@Slf4j
public class ProcessInstanceResource {

  @Inject KafkaStreams kafkaStreams;

  @Inject Client client;
  @Inject TaktConfiguration taktConfiguration;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<UUID> getProcessInstances() {
    List<UUID> processInstances = new ArrayList<>();
    ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore =
        getProcessInstanceStore();

    Collection<StreamsMetadata> streamsMetadata =
        kafkaStreams.streamsMetadataForStore(
            taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()));

    streamsMetadata.forEach(
        metadata -> {
          System.out.println("Host: " + metadata.host());
          System.out.println("Port: " + metadata.port());
        });
    try (KeyValueIterator<UUID, ProcessInstanceDTO> all = processInstanceStore.all()) {
      all.forEachRemaining(
          processInstanceRecord -> processInstances.add(processInstanceRecord.key));
    }
    return processInstances;
  }

  private ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO> getProcessInstanceStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<UUID, ProcessInstanceDTO>>
            processInstanceStoreQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
                    QueryableStoreTypes.keyValueStore());
        return kafkaStreams.store(processInstanceStoreQueryParameters);
      } catch (InvalidStateStoreException e) {
        // ignore, store not ready yet
      }
    }
  }

  private ReadOnlyKeyValueStore<VariableKeyDTO, JsonNode> getVariablesStore() {
    while (true) {
      try {
        StoreQueryParameters<? extends ReadOnlyKeyValueStore<VariableKeyDTO, JsonNode>>
            storeQueryParameters =
                StoreQueryParameters.fromNameAndType(
                    taktConfiguration.getPrefixed(Stores.VARIABLES.getStorename()),
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

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
            processId,
            new TaktUUIDSerializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      log.info("no metadata available for key {}", processId);
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(taktConfiguration.getHost())
        && metadata.activeHost().port() == taktConfiguration.getPort()) {
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
          taktConfiguration.getHost(),
          taktConfiguration.getPort(),
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

    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            taktConfiguration.getPrefixed(Stores.VARIABLES.getStorename()),
            processId,
            new TaktUUIDSerializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(taktConfiguration.getHost())
        && metadata.activeHost().port() == taktConfiguration.getPort()) {

      Map<String, JsonNode> variables = new HashMap<>();
      FlowNodeInstanceKeyDTO minKey =
          new FlowNodeInstanceKeyDTO(processId, List.of(Constants.MIN_LONG));
      FlowNodeInstanceKeyDTO maxKey =
          new FlowNodeInstanceKeyDTO(processId, List.of(Constants.MAX_LONG));
      VariableKeyDTO startVariableKey = new VariableKeyDTO(minKey, "");
      VariableKeyDTO endVariableKey = new VariableKeyDTO(maxKey, "\u00ff");
      try (KeyValueIterator<VariableKeyDTO, JsonNode> range =
          getVariablesStore().range(startVariableKey, endVariableKey)) {
        range.forEachRemaining(entry -> variables.put(entry.key.getVariableName(), entry.value));
      }
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
      throw new IllegalArgumentException(e);
    }
  }

  private URI getOtherUriForProcessInstanceVariables(String host, int port, UUID id) {
    try {
      return new URI("http://" + host + ":" + port + "/process-instances/" + id + "/variables");
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
