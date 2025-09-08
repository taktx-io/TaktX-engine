/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.taktx.app.websocket.JsonUtils;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Path("/processinstances")
@Slf4j
public class InstanceResource {

  @Inject InstanceUpdateRegistry instanceUpdateRegistry;

  @GET
  @Path("/{processDefinitionId}/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProcessInstancesByDefinition(
      @PathParam("processDefinitionId") String processDefinitionId,
      @PathParam("version") Integer version,
      @QueryParam("limit") Integer limit) {

    try {
      ProcessDefinitionKey key = new ProcessDefinitionKey(processDefinitionId, version);

      // Get process instance IDs for the given definition key
      List<InstanceUpdateRecord> instances =
          instanceUpdateRegistry.getProcessInstancesByDefinition(key, limit != null ? limit : 100);

      return Response.ok(JsonUtils.toJsonStringWithFieldNames(instances)).build();
    } catch (Exception e) {
      log.error("Error retrieving process instances", e);
      return Response.serverError().entity("Error retrieving process instances").build();
    }
  }
}
