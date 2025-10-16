/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.taktx.client.TaktClient;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/signals")
@Slf4j
@RequiredArgsConstructor
public class SignalResource {
  private final TaktClient taktClient;

  @POST
  @Path("/{signal}")
  public void startProcessInstance(@PathParam("signal") String signal) {
    taktClient.sendSignal(signal);
  }
}
