/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.quarkus.scheduler.Scheduled;
import io.taktx.app.InstanceUpdateRegistry;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ServerEndpoint("/ws/process-definitions")
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ProcessDefinitionCountsWebSocket {

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final InstanceUpdateRegistry instanceUpdateRegistry;

  @OnOpen
  public void onOpen(Session session) {
    sessions.put(session.getId(), session);
    log.info("WebSocket session opened: {}", session.getId());
    sendProcessDefinitionCounts(session);
  }

  @OnClose
  public void onClose(Session session) {
    sessions.remove(session.getId());
    log.info("WebSocket session closed: {}", session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.error(
        "WebSocket error for session {}: {}", session.getId(), throwable.getMessage(), throwable);
    sessions.remove(session.getId());
  }

  @Scheduled(every = "1s")
  void updateProcessInstanceCounts() {
    if (sessions.isEmpty()) {
      return;
    }

    // Broadcast update to relevant sessions
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      sendProcessDefinitionCounts(entry.getValue());
    }
  }

  private void sendProcessDefinitionCounts(Session session) {
    Map<ProcessDefinitionKey, AtomicInteger> processDefinitionCountsStarted =
        instanceUpdateRegistry.getProcessDefinitionCountsStarted();
    Map<ProcessDefinitionKey, AtomicInteger> processDefinitionCountsCompleted =
        instanceUpdateRegistry.getProcessDefinitionCountsCompleted();

    Map<String, Counts> countsMap = new HashMap<>();
    processDefinitionCountsStarted.forEach(
        (key, value) -> {
          Counts counts =
              countsMap.computeIfAbsent(
                  key.getProcessDefinitionId() + "." + key.getVersion(), (k) -> new Counts());
          counts.started = value.get();
          counts.completed = processDefinitionCountsCompleted.get(key).get();
        });
    // Format and send only to this session
    String message =
        JsonUtils.toJsonString(Map.of("type", "processDefinitionCounts", "data", countsMap));

    session.getAsyncRemote().sendText(message);
  }

  @Data
  private static class Counts {
    int started;
    int completed;
  }
}
