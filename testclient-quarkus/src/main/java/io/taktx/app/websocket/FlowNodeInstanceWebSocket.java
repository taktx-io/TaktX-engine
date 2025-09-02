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
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket endpoint for real-time flow node instance updates.
 * 
 * This class provides a WebSocket endpoint that allows clients to subscribe to
 * flow node updates for a specific process instance. When a flow node state changes
 * (e.g., becomes active, completes), this endpoint will broadcast that update to
 * all clients subscribed to the particular process instance.
 * 
 * The path pattern /ws/flow-node-instances/{processInstanceId} allows clients to
 * subscribe to updates for a specific process instance by ID.
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
    @Inject
    TaktClient taktClient;
    
    /**
     * Handles new WebSocket connections.
     * 
     * When a client connects, we register them as interested in updates for a specific
     * process instance. If this is the first client to connect, we also register a
     * consumer with TaktClient to receive flow node updates.
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
            log.info("WebSocket session opened for process instance {}: {}", processInstanceId, session.getId());
            
            // Register consumer for flow node instance updates if first client
            if (sessions.size() == 1) {
                log.info("First WebSocket client connected, registering flow node instance update consumer");
                taktClient.registerInstanceUpdateConsumer(this::handleFlowNodeUpdate);
            }
            
            // Send message to acknowledge connection
            session.getAsyncRemote().sendText(JsonUtils.toJsonString(Map.of(
                "type", "connected",
                "processInstanceId", processInstanceId
            )));
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid process instance ID format: {}", processInstanceIdStr);
            session.getAsyncRemote().sendText(JsonUtils.toJsonString(Map.of(
                "type", "error",
                "message", "Invalid process instance ID format"
            )));
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
     * When a client disconnects, we remove them from our session tracking maps.
     * 
     * @param session The WebSocket session that was closed
     */
    @OnClose
    public void onClose(Session session) {
        UUID processInstanceId = sessionToProcessInstanceId.remove(session.getId());
        sessions.remove(session.getId());
        log.info("WebSocket session closed for process instance {}: {}", processInstanceId, session.getId());
    }
    
    /**
     * Handles WebSocket errors.
     * 
     * If an error occurs with a WebSocket connection, we log the error and clean up
     * the session from our tracking maps.
     * 
     * @param session The WebSocket session where the error occurred
     * @param throwable The error that occurred
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        UUID processInstanceId = sessionToProcessInstanceId.get(session.getId());
        log.error("WebSocket error for session {} (process instance {}): {}", 
                session.getId(), processInstanceId, throwable.getMessage(), throwable);
        sessionToProcessInstanceId.remove(session.getId());
        sessions.remove(session.getId());
    }
    
    /**
     * Callback handler for flow node updates from TaktClient.
     * 
     * This method is invoked whenever a flow node status changes. It filters updates
     * to only process FLOWNODE update types and forwards them to clients subscribed
     * to the specific process instance.
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
        String jsonMessage = JsonUtils.toJsonString(Map.of(
            "type", "flow-node-update",
            "processInstanceId", processInstanceId,
            "flowNodeInstance", flowNodeInstanceUpdateDTO.getFlowNodeInstance(),
            "timestamp", System.currentTimeMillis()
        ));
        
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
