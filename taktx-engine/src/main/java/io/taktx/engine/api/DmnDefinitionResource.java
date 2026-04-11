/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.api;

import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionKey;
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
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Path("/dmn-definitions")
public class DmnDefinitionResource {

  private ReadOnlyKeyValueStore<DmnDefinitionKey, ValueAndTimestamp<DmnDefinitionDTO>>
      definitionStore;
  private ReadOnlyKeyValueStore<DmnDefinitionKey, String> xmlStore;

  @Inject KafkaStreams kafkaStreams;
  @Inject TaktConfiguration taktConfiguration;

  @PostConstruct
  void init() {
    definitionStore =
        kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Stores.GLOBAL_DMN_DEFINITION.getStorename()),
                QueryableStoreTypes.timestampedKeyValueStore()));
    xmlStore =
        kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Stores.XML_BY_DMN_DEFINITION_ID.getStorename()),
                QueryableStoreTypes.keyValueStore()));
  }

  /**
   * Returns all deployed DMN definition keys ({@code id.version}).
   *
   * <pre>GET /dmn-definitions</pre>
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<DmnDefinitionKey> getDmnDefinitionKeys() {
    List<DmnDefinitionKey> keys = new ArrayList<>();
    try (KeyValueIterator<DmnDefinitionKey, ValueAndTimestamp<DmnDefinitionDTO>> all =
        definitionStore.all()) {
      all.forEachRemaining(entry -> keys.add(entry.key));
    }
    return keys;
  }

  /**
   * Returns the parsed DMN definition (decisions, hit policies, DRG requirements, …).
   *
   * <pre>GET /dmn-definitions/{dmnDefinitionKey}</pre>
   *
   * <p>{@code dmnDefinitionKey} format: {@code <id>} (latest version) or {@code <id>.<version>}.
   */
  @GET
  @Path("/{dmnDefinitionKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDmnDefinition(@PathParam("dmnDefinitionKey") String dmnDefinitionKeyString) {
    DmnDefinitionKey key = parseKey(dmnDefinitionKeyString);
    ValueAndTimestamp<DmnDefinitionDTO> vt = definitionStore.get(key);
    if (vt == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(vt.value()).build();
  }

  /**
   * Returns the raw DMN XML for a specific definition version.
   *
   * <pre>GET /dmn-definitions/{dmnDefinitionKey}/xml</pre>
   */
  @GET
  @Path("/{dmnDefinitionKey}/xml")
  @Produces(MediaType.APPLICATION_XML)
  public Response getDmnDefinitionXml(
      @PathParam("dmnDefinitionKey") String dmnDefinitionKeyString) {
    DmnDefinitionKey key = parseKey(dmnDefinitionKeyString);
    String xml = xmlStore.get(key);
    if (xml == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(xml).build();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static DmnDefinitionKey parseKey(String raw) {
    String[] parts = raw.split("\\.");
    String id = parts[0];
    Integer version = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
    return new DmnDefinitionKey(id, version);
  }
}
