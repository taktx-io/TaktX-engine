/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.taktx.client.TaktClient;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Path("/processdefinitions")
@Slf4j
public class DefinitionResource {

  @Inject TaktClient taktClient;

  @GET
  @Path("/{processDefinitionId}")
  public String startProcessInstance(
      @PathParam("processDefinitionId") String processDefinitionIdVersionString)
      throws IOException {
    String[] split = processDefinitionIdVersionString.split("\\.");
    String processDefinitionId = split[0];
    Integer version = Integer.parseInt(split[1]);
    ProcessDefinitionKey processDefinitionKey =
        new ProcessDefinitionKey(processDefinitionId, version);
    return taktClient.getProcessDefinitionXml(processDefinitionKey);
  }
}
