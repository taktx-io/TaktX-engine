/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.taktx.client.TaktClient;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.InstanceUpdateDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket endpoint for real-time flow node instance updates.
 *
 * <p>This class provides a WebSocket endpoint that allows clients to subscribe to flow node updates
 * for a specific process instance. When a flow node state changes (e.g., becomes active,
 * completes), this endpoint will broadcast that update to all clients subscribed to the particular
 * process instance.
 *
 * <p>The path pattern /ws/flow-node-instances/{processInstanceId} allows clients to subscribe to
 * updates for a specific process instance by ID.
 */
@ServerEndpoint("/ws/flow-node-instances/{processInstanceId}")
@ApplicationScoped
@Slf4j
public class FlowNodeInstanceWebSocket {

  /** Stores all active WebSocket sessions keyed by session ID */
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  /** Maps session IDs to process instance IDs for tracking subscriptions */
  private final Map<String, UUID> sessionToProcessInstanceId = new ConcurrentHashMap<>();

  /** TaktClient used to register callbacks for flow node updates */
  @Inject TaktClient taktClient;

  /**
   * Handles new WebSocket connections.
   *
   * <p>When a client connects, we register them as interested in updates for a specific process
   * instance. If this is the first client to connect, we also register a consumer with TaktClient
   * to receive flow node updates.
   *
   * @param session The WebSocket session that was opened
   * @param processInstanceIdStr The process instance ID to subscribe to (from path parameter)
   */
  @OnOpen
  public void onOpen(Session session, @PathParam("processInstanceId") String processInstanceIdStr) {
    try {
      UUID processInstanceId = UUID.fromString(processInstanceIdStr);
      sessions.put(session.getId(), session);
      sessionToProcessInstanceId.put(session.getId(), processInstanceId);
      log.info(
          "WebSocket session opened for process instance {}: {}",
          processInstanceId,
          session.getId());

      // Register consumer for flow node instance updates if first client
      if (sessions.size() == 1) {
        log.info(
            "First WebSocket client connected, registering flow node instance update consumer");
        taktClient.registerInstanceUpdateConsumer(this::handleFlowNodeUpdate);
      }

      // Send message to acknowledge connection
      session
          .getAsyncRemote()
          .sendText(
              JsonUtils.toJsonString(
                  Map.of("type", "connected", "processInstanceId", processInstanceId)));

    } catch (IllegalArgumentException e) {
      log.error("Invalid process instance ID format: {}", processInstanceIdStr);
      session
          .getAsyncRemote()
          .sendText(
              JsonUtils.toJsonString(
                  Map.of(
                      "type", "error",
                      "message", "Invalid process instance ID format")));
      try {
        session.close();
      } catch (Exception ex) {
        log.error("Error closing session", ex);
      }
    }
  }

  /**
   * Handles WebSocket disconnections.
   *
   * <p>When a client disconnects, we remove them from our session tracking maps.
   *
   * @param session The WebSocket session that was closed
   */
  @OnClose
  public void onClose(Session session) {
    UUID processInstanceId = sessionToProcessInstanceId.remove(session.getId());
    sessions.remove(session.getId());
    log.info(
        "WebSocket session closed for process instance {}: {}", processInstanceId, session.getId());
  }

  /**
   * Handles WebSocket errors.
   *
   * <p>If an error occurs with a WebSocket connection, we log the error and clean up the session
   * from our tracking maps.
   *
   * @param session The WebSocket session where the error occurred
   * @param throwable The error that occurred
   */
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

  /**
   * Handles incoming WebSocket messages from clients.
   *
   * <p>Currently handles the "get_current_state" action which returns the current state of flow
   * nodes for the subscribed process instance.
   *
   * @param session The WebSocket session that sent the message
   * @param message The message received from the client
   */
  @OnMessage
  public void onMessage(Session session, String message) {
    try {
      // Parse the message as JSON
      Map<String, Object> messageMap = JsonUtils.fromJsonString(message, Map.class);
      String action = (String) messageMap.get("action");

      if ("get_current_state".equals(action)) {
        // Get the process instance ID for this session
        UUID processInstanceId = sessionToProcessInstanceId.get(session.getId());
        if (processInstanceId == null) {
          log.warn(
              "Session {} requested flow node state but has no associated process instance ID",
              session.getId());
          return;
        }

        // Get the current flow node instances for this process instance
        List<Map<String, Object>> activeFlowNodes = getCurrentFlowNodeState(processInstanceId);

        // Send back the flow nodes
        String responseJson =
            JsonUtils.toJsonStringWithFieldNames(
                Map.of(
                    "type",
                    "flow-node-state",
                    "processInstanceId",
                    processInstanceId,
                    "activeFlowNodes",
                    activeFlowNodes,
                    "timestamp",
                    System.currentTimeMillis()));

        session.getAsyncRemote().sendText(responseJson);
      }
    } catch (Exception e) {
      log.error("Error processing WebSocket message: {}", e.getMessage(), e);
      try {
        session
            .getAsyncRemote()
            .sendText(
                JsonUtils.toJsonString(
                    Map.of(
                        "type",
                        "error",
                        "message",
                        "Error processing request: " + e.getMessage())));
      } catch (Exception ex) {
        log.error("Error sending error response", ex);
      }
    }
  }

  /**
   * Retrieves the current state of flow nodes for a given process instance.
   *
   * @param processInstanceId The ID of the process instance to get flow nodes for
   * @return A list of active flow nodes
   */
  private List<Map<String, Object>> getCurrentFlowNodeState(UUID processInstanceId) {
    // Since TaktClient doesn't have a direct getActiveFlowNodes method,
    // we'll use a workaround to fetch the current state
    try {
      // Create an empty list to store our results
      List<Map<String, Object>> activeFlowNodes = new ArrayList<>();

      // Add a mock flow node for now - this will be replaced with actual implementation
      // once the API for retrieving active flow nodes is available
      Map<String, Object> mockFlowNode = new HashMap<>();
      mockFlowNode.put(
          "flowNodeId", "StartEvent_1"); // This is a common ID for start events in BPMN
      mockFlowNode.put("flowNodeType", "startEvent");
      mockFlowNode.put("state", "COMPLETED");
      activeFlowNodes.add(mockFlowNode);

      // Add another node that's likely to be active
      Map<String, Object> mockActiveNode = new HashMap<>();
      mockActiveNode.put("flowNodeId", "Task_1"); // This assumes you have a task with this ID
      mockActiveNode.put("flowNodeType", "userTask");
      mockActiveNode.put("state", "ACTIVE");
      activeFlowNodes.add(mockActiveNode);

      log.info("Returning mock flow node state for process instance {}", processInstanceId);
      return activeFlowNodes;

      // TODO: Replace with actual implementation once the API is available:
      // return taktClient.getSomeExistingMethod(processInstanceId).stream()
      //     .map(flowNode -> Map.of(
      //         "flowNodeId", flowNode.getFlowNodeId(),
      //         "flowNodeType", flowNode.getFlowNodeType(),
      //         "state", flowNode.getState()
      //     ))
      //     .collect(Collectors.toList());
    } catch (Exception e) {
      log.error(
          "Error retrieving flow node state for process instance {}: {}",
          processInstanceId,
          e.getMessage(),
          e);
      // Return an empty list if there's an error
      return new ArrayList<>();
    }
  }

  /**
   * Callback handler for flow node updates from TaktClient.
   *
   * <p>This method is invoked whenever a flow node status changes. It filters updates to only
   * process FLOWNODE update types and forwards them to clients subscribed to the specific process
   * instance.
   *
   * @param processInstanceId The ID of the process instance that had a flow node update
   * @param update The update data containing flow node state information
   */
  private void handleFlowNodeUpdate(UUID processInstanceId, InstanceUpdateDTO update) {
    if (sessions.isEmpty()) {
      return;
    }

    // Skip if this isn't a FLOWNODE update
    if (!(update instanceof FlowNodeInstanceUpdateDTO flowNodeInstanceUpdateDTO)) {
      return;
    }

    // Format message
    String jsonMessage =
        JsonUtils.toJsonStringWithFieldNames(
            Map.of(
                "type",
                "flow-node-update",
                "processInstanceId",
                processInstanceId,
                "flowNodeInstance",
                flowNodeInstanceUpdateDTO.getFlowNodeInstance(),
                "timestamp",
                System.currentTimeMillis()));

    // Broadcast update to relevant sessions (those subscribed to this process instance)
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      String sessionId = entry.getKey();
      UUID subscribedInstanceId = sessionToProcessInstanceId.get(sessionId);

      if (processInstanceId.equals(subscribedInstanceId)) {

        entry.getValue().getAsyncRemote().sendText(jsonMessage);
      }
    }
  }
}
