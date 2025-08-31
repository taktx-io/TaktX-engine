/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.api;

import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@Path("/process-definitions")
public class ProcessDefinitionResource {
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> definitionStore;
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, String> xmlStore;

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
    definitionStore = kafkaStreams.store(storeQueryParameters);
    StoreQueryParameters<? extends ReadOnlyKeyValueStore<ProcessDefinitionKey, String>>
        xmlStoreQueryParameters =
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Stores.XML_BY_PROCESS_DEFINITION_ID.getStorename()),
                QueryableStoreTypes.keyValueStore());
    xmlStore = kafkaStreams.store(xmlStoreQueryParameters);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionKey> getProcessDefinitionKeys() {
    List<ProcessDefinitionKey> processDefinitionKeys = new ArrayList<>();
    try (KeyValueIterator<ProcessDefinitionKey, ProcessDefinitionDTO> all = definitionStore.all()) {
      all.forEachRemaining(pdRecord -> processDefinitionKeys.add(pdRecord.key));
    }
    return processDefinitionKeys;
  }

  @GET
  @Path("/{processDefinitionKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessDefinition(
      @PathParam("processDefinitionKey") String processDefinitionKeyString) {

    ProcessDefinitionKey processDefinitionKey = getProcessDefinitionKey(processDefinitionKeyString);

    ProcessDefinitionDTO processDefinition = definitionStore.get(processDefinitionKey);
    if (processDefinition == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else {
      return Response.ok(processDefinition).build();
    }
  }

  @GET
  @Path("/{processDefinitionKey}/xml")
  @Produces(MediaType.APPLICATION_XML)
  public Response getProcessDefinitionXml(
      @PathParam("processDefinitionKey") String processDefinitionKeyString) {

    ProcessDefinitionKey processDefinitionKey = getProcessDefinitionKey(processDefinitionKeyString);

    String xml = xmlStore.get(processDefinitionKey);
    if (xml == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } else {
      return Response.ok(xml).build();
    }
  }

  private static ProcessDefinitionKey getProcessDefinitionKey(String processDefinitionKeyString) {
    String[] split = processDefinitionKeyString.split("\\.");
    String processDefinitionName = split[0];
    Integer processDefinitionVersion = split.length > 1 ? Integer.parseInt(split[1]) : -1;

    return new ProcessDefinitionKey(processDefinitionName, processDefinitionVersion);
  }
}
