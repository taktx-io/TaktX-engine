/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.api;

import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pd.Stores;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-definitions")
public class ProcessDefinitionResource {
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> store;
  @Inject KafkaStreams kafkaStreams;
  @Inject TaktConfiguration taktConfiguration;

  @PostConstruct
  void init() {
    StoreQueryParameters<
            ? extends ReadOnlyKeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO>>
        storeQueryParameters =
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()),
                QueryableStoreTypes.keyValueStore());
    store = kafkaStreams.store(storeQueryParameters);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionKey> getProcessDefinitionKeys() {
    List<ProcessDefinitionKey> processDefinitionKeys = new ArrayList<>();

    Collection<StreamsMetadata> streamsMetadata =
        kafkaStreams.streamsMetadataForStore(
            taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
    streamsMetadata.forEach(
        metadata -> {
          System.out.println("Host: " + metadata.host());
          System.out.println("Port: " + metadata.port());
        });
    try (KeyValueIterator<ProcessDefinitionKey, ProcessDefinitionDTO> all = store.all()) {
      all.forEachRemaining(pdRecord -> processDefinitionKeys.add(pdRecord.key));
    }
    return processDefinitionKeys;
  }

  @GET
  @Path("/{processDefinitionKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessDefinition(
      @PathParam("processDefinitionKey") String processDefinitionKeyString) {

    String[] split = processDefinitionKeyString.split("\\.");
    String processDefinitionName = split[0];
    Integer processDefinitionVersion = Integer.parseInt(split[1]);
    ProcessDefinitionKey processDefinitionKey =
        new ProcessDefinitionKey(processDefinitionName, processDefinitionVersion);
    KeyQueryMetadata metadata =
        kafkaStreams.queryMetadataForKey(
            taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()),
            processDefinitionKey,
            TopologyProducer.PROCESS_DEFINITION_KEY_SERDE.serializer());

    if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else if (metadata.activeHost().host().equals(taktConfiguration.getHost()) && metadata.activeHost().port() == taktConfiguration.getPort()) {
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
      throw new IllegalStateException(e);
    }
  }
}
