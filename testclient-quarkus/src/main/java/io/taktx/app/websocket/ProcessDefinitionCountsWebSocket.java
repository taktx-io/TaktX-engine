/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.quarkus.scheduler.Scheduled;
import io.taktx.app.InstanceUpdateConsumer;
import io.taktx.app.InstanceUpdateRegistry;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

  @PostConstruct
  void init() {
    // Register to receive process instance updates for broadcasting
    instanceUpdateRegistry.registerInstanceUpdateConsumer(
        new InstanceUpdateConsumer() {
          @Override
          public void processInstanceUpdate(
              long timestamp, UUID processInstanceId, ProcessInstanceUpdateDTO update) {
            broadcastProcessInstanceUpdate(timestamp, processInstanceId, update);
          }

          @Override
          public void flowNodeInstanceUpdate(
              long timestamp, UUID processInstanceId, FlowNodeInstanceUpdateDTO update) {
            // We don't need to broadcast flow node updates globally
            // Those are handled by the per-instance websocket
          }
        });
  }

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

  private void broadcastProcessInstanceUpdate(
      long timestamp, UUID processInstanceId, ProcessInstanceUpdateDTO update) {
    if (sessions.isEmpty()) {
      return;
    }

    // Create the message format
    String message =
        JsonUtils.toJsonString(
            Map.of(
                "type",
                "processInstanceUpdate",
                "timestamp",
                timestamp,
                "processInstanceId",
                processInstanceId,
                "update",
                JsonUtils.toJsonNodeWithFieldNames(update)));

    // Broadcast to all connected sessions
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      Session session = entry.getValue();
      try {
        session.getAsyncRemote().sendText(message);
      } catch (Exception e) {
        log.warn(
            "Failed to send process instance update to session {}: {}",
            session.getId(),
            e.getMessage());
      }
    }
  }

  @Data
  private static class Counts {
    int started;
    int completed;
  }
}
