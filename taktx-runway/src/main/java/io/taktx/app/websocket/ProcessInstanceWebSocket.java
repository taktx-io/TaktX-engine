/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.taktx.app.InstanceUpdateConsumer;
import io.taktx.app.InstanceUpdateRegistry;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ServerEndpoint("/ws/process-instance/{processInstanceId}")
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ProcessInstanceWebSocket {

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Map<String, UUID> sessionToProcessInstanceId = new ConcurrentHashMap<>();

  private final InstanceUpdateRegistry instanceUpdateRegistry;

  @PostConstruct
  void init() {
    instanceUpdateRegistry.registerInstanceUpdateConsumer(
        new InstanceUpdateConsumer() {
          @Override
          public void processInstanceUpdate(
              long timestamp, UUID processInstanceId, ProcessInstanceUpdateDTO update) {
            sendProcessInstanceUpdate(timestamp, processInstanceId, update);
          }

          @Override
          public void flowNodeInstanceUpdate(
              long timestamp, UUID processInstanceId, FlowNodeInstanceUpdateDTO update) {
            sendFlowNodeInstanceUpdate(timestamp, processInstanceId, update);
          }
        });
  }

  @OnOpen
  public void onOpen(Session session, @PathParam("processInstanceId") String processInstanceId) {
    sessions.put(session.getId(), session);
    UUID processInstanceUuid = UUID.fromString(processInstanceId);
    sessionToProcessInstanceId.put(session.getId(), processInstanceUuid);
    log.info(
        "WebSocket session opened for process instance {} {}", processInstanceId, session.getId());

    InstanceUpdateRecord processInstance =
        instanceUpdateRegistry.getProcessInstance(processInstanceUuid);
    sendProcessInstanceUpdate(
        processInstance.getTimestamp(),
        processInstanceUuid,
        (ProcessInstanceUpdateDTO) processInstance.getUpdate());
    List<InstanceUpdateRecord> instanceUpdateRecords =
        instanceUpdateRegistry.getScopeByProcessInstance(processInstanceUuid);
    for (InstanceUpdateRecord instanceUpdateRecord : instanceUpdateRecords) {
      sendFlowNodeInstanceUpdate(
          instanceUpdateRecord.getTimestamp(),
          processInstanceUuid,
          (FlowNodeInstanceUpdateDTO) instanceUpdateRecord.getUpdate());
    }
  }

  @OnClose
  public void onClose(Session session) {
    UUID processInstanceId = sessionToProcessInstanceId.remove(session.getId());
    sessions.remove(session.getId());
    log.info(
        "WebSocket session closed for process instance {}: {}", processInstanceId, session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    UUID processInstanceId = sessionToProcessInstanceId.get(session.getId());
    log.error(
        "WebSocket error for session {} (process instance {}): {}",
        session.getId(),
        processInstanceId,
        throwable.getMessage(),
        throwable);
    sessionToProcessInstanceId.remove(session.getId());
    sessions.remove(session.getId());
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    // Handle any client messages, if needed
  }

  private void sendFlowNodeInstanceUpdate(
      long timestamp, UUID processInstanceId, FlowNodeInstanceUpdateDTO update) {
    // Broadcast update to relevant sessions
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      Session session = entry.getValue();
      UUID uuid = sessionToProcessInstanceId.get(entry.getKey());

      if (processInstanceId.equals(uuid)) {
        // Format and send only to this session
        String message =
            JsonUtils.toJsonString(
                Map.of(
                    "type",
                    "flowNodeInstanceUpdate",
                    "timestamp",
                    timestamp,
                    "processInstanceId",
                    processInstanceId,
                    "update",
                    JsonUtils.toJsonNodeWithFieldNames(update)));
        log.info(
            "Broadcast flowNodeInstanceUpdate {} for pid {}",
            processInstanceId,
            update.getFlowNodeInstancePath());
        session.getAsyncRemote().sendText(message);
      }
    }
  }

  private void sendProcessInstanceUpdate(
      long timestamp, UUID processInstanceId, ProcessInstanceUpdateDTO update) {
    // Broadcast update to relevant sessions
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      Session session = entry.getValue();
      UUID uuid = sessionToProcessInstanceId.get(entry.getKey());
      if (processInstanceId.equals(uuid)) {
        // Format and send only to this session
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
        log.info("Broadcast processInstanceUpdate for pid {}", processInstanceId);
        session.getAsyncRemote().sendText(message);
      }
    }
  }
}
