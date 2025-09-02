/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.websocket;

import io.taktx.client.TaktClient;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@ServerEndpoint("/ws/process-definitions")
@ApplicationScoped
@Slf4j
public class ProcessDefinitionWebSocket {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    @Inject
    TaktClient taktClient;
    
    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket session opened: {}", session.getId());
        
        // Register consumer for process definition updates
        if (sessions.size() == 1) {
            log.info("First WebSocket client connected, registering process definition update consumer");
            taktClient.registerProcessDefinitionUpdateConsumer(this::broadcastUpdate);
        }
        
        // Send current process definitions to the new client
        Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitions = 
            taktClient.getProcessDefinitionConsumer().getDeployedProcessDefinitions();
        
        // Format and send only to this session
        String message = JsonUtils.toJsonString(Map.of(
            "type", "initial",
            "data", definitions
        ));
        
        session.getAsyncRemote().sendText(message);
    }
    
    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        log.info("WebSocket session closed: {}", session.getId());
    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error for session {}: {}", session.getId(), throwable.getMessage(), throwable);
        sessions.remove(session.getId());
    }
    
    private void broadcastUpdate(ProcessDefinitionKey key, ProcessDefinitionDTO definition) {
        if (sessions.isEmpty()) {
            return;
        }
        
        String message = JsonUtils.toJsonString(Map.of(
            "type", "update",
            "key", key,
            "definition", definition
        ));
        
        sessions.values().forEach(session -> {
            session.getAsyncRemote().sendText(message);
        });
        
        log.debug("Broadcasted process definition update to {} clients", sessions.size());
    }
}
