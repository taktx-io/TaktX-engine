/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.taktx.client.TaktClient;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

@ServerEndpoint("/ws/process-instances/{processDefinitionId}")
@ApplicationScoped
@Slf4j
public class ProcessInstanceWebSocket {

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToProcessDefinitionId = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<Map<String, Object>>> processInstanceUpdates =
      new ConcurrentHashMap<>();
  private static final int MAX_STORED_INSTANCES = 100;

  @Inject TaktClient taktClient;

  @OnOpen
  public void onOpen(
      Session session, @PathParam("processDefinitionId") String processDefinitionId) {
    sessions.put(session.getId(), session);
    sessionToProcessDefinitionId.put(session.getId(), processDefinitionId);
    log.info(
        "WebSocket session opened for process definition {}: {}",
        processDefinitionId,
        session.getId());

    // Register consumer for process instance updates if first client
    if (sessions.size() == 1) {
      log.info("First WebSocket client connected, registering process instance update consumer");
      taktClient.registerInstanceUpdateConsumer(this::handleInstanceUpdate);
    }

    // Initialize storage for this process definition if not already done
    processInstanceUpdates.putIfAbsent(processDefinitionId, new CopyOnWriteArrayList<>());

    // Send current process instances to the new client
    CopyOnWriteArrayList<Map<String, Object>> instances =
        processInstanceUpdates.get(processDefinitionId);

    // Format and send only to this session
    String message =
        JsonUtils.toJsonString(
            Map.of(
                "type", "initial",
                "processDefinitionId", processDefinitionId,
                "data", instances));

    session.getAsyncRemote().sendText(message);
  }

  @OnClose
  public void onClose(Session session) {
    String processDefinitionId = sessionToProcessDefinitionId.remove(session.getId());
    sessions.remove(session.getId());
    log.info(
        "WebSocket session closed for process definition {}: {}",
        processDefinitionId,
        session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    String processDefinitionId = sessionToProcessDefinitionId.get(session.getId());
    log.error(
        "WebSocket error for session {} (process definition {}): {}",
        session.getId(),
        processDefinitionId,
        throwable.getMessage(),
        throwable);
    sessionToProcessDefinitionId.remove(session.getId());
    sessions.remove(session.getId());
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    // Handle any client messages, if needed
  }

  private void handleInstanceUpdate(UUID processInstanceKey, InstanceUpdateDTO update) {
    if (sessions.isEmpty()) {
      return;
    }

    if (!(update instanceof ProcessInstanceUpdateDTO processInstanceUpdateDTO)) {
      return;
    }

    // Extract process definition ID from update
    ProcessDefinitionKey processDefinitionKey = processInstanceUpdateDTO.getProcessDefinitionKey();
    String processDefinitionId = processDefinitionKey.getProcessDefinitionId();

    // Skip if no clients are subscribed to this process definition
    if (!sessionToProcessDefinitionId.containsValue(processDefinitionId)) {
      return;
    }

    // Create a simplified update object
    Map<String, Object> instanceUpdate =
        Map.of(
            "processInstanceId", processInstanceKey,
            "processDefinitionId", processDefinitionId,
            "version", processDefinitionKey.getVersion(),
            "processInstanceUpdate", processInstanceUpdateDTO,
            "timestamp", System.currentTimeMillis());

    // Store update in memory (limited to MAX_STORED_INSTANCES)
    processInstanceUpdates.putIfAbsent(processDefinitionId, new CopyOnWriteArrayList<>());
    CopyOnWriteArrayList<Map<String, Object>> instances =
        processInstanceUpdates.get(processDefinitionId);

    synchronized (instances) {
      instances.add(0, instanceUpdate);
      if (instances.size() > MAX_STORED_INSTANCES) {
        instances.remove(instances.size() - 1);
      }
    }

    // Format message
    String jsonMessage =
        JsonUtils.toJsonString(
            Map.of(
                "type", "update",
                "processDefinitionId", processDefinitionId,
                "data", instanceUpdate));

    // Broadcast update to relevant sessions
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      String sessionId = entry.getKey();
      String subscribedDefinitionId = sessionToProcessDefinitionId.get(sessionId);

      if (processDefinitionId.equals(subscribedDefinitionId)) {
        entry.getValue().getAsyncRemote().sendText(jsonMessage);
      }
    }
  }
}
