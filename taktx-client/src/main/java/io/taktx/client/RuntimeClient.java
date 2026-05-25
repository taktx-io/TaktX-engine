/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Focused public facet for deployment, runtime interaction, and task completion operations. */
public final class RuntimeClient {

  private final TaktXClient client;

  RuntimeClient(TaktXClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  public ParsedDefinitionsDTO deployProcessDefinition(InputStream inputStream) throws IOException {
    return client.deployProcessDefinition(inputStream);
  }

  public io.taktx.dto.ParsedDmnDefinitionsDTO deployDmnDefinition(InputStream inputStream)
      throws IOException {
    return client.deployDmnDefinition(inputStream);
  }

  public Optional<ProcessDefinitionDTO> getProcessDefinitionByHash(
      String processDefinitionId, String hash) {
    return client.getProcessDefinitionByHash(processDefinitionId, hash);
  }

  public void registerInstanceUpdateConsumer(
      String groupId, Consumer<List<InstanceUpdateRecord>> consumer) {
    client.registerInstanceUpdateConsumer(groupId, consumer);
  }

  public void registerInstanceUpdateConsumer(
      String groupId,
      Consumer<List<InstanceUpdateRecord>> consumer,
      InstanceUpdateStartStrategy strategy) {
    client.registerInstanceUpdateConsumer(groupId, consumer, strategy);
  }

  public void registerProcessDefinitionUpdateConsumer(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    client.registerProcessDefinitionUpdateConsumer(consumer);
  }

  public UUID startProcess(String process, VariablesDTO variables) {
    return client.startProcess(process, variables);
  }

  public UUID startProcess(String process, int version, VariablesDTO variables) {
    return client.startProcess(process, version, variables);
  }

  public UUID startProcess(
      String process, int version, VariablesDTO variables, @Nullable String authorizationToken) {
    return client.startProcess(process, version, variables, authorizationToken);
  }

  public UUID startProcess(
      String process, VariablesDTO variables, @Nullable String businessKey, Set<String> tags) {
    return client.startProcess(process, variables, businessKey, tags);
  }

  public UUID startProcess(
      String process,
      int version,
      VariablesDTO variables,
      @Nullable String businessKey,
      Set<String> tags,
      @Nullable String authorizationToken) {
    return client.startProcess(process, version, variables, businessKey, tags, authorizationToken);
  }

  public void sendMessage(MessageEventDTO messageEventDTO) {
    client.sendMessage(messageEventDTO);
  }

  public void sendSignal(String signalName) {
    client.sendSignal(signalName);
  }

  public ExternalTaskInstanceResponder respondToExternalTask(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return client.respondToExternalTask(externalTaskTriggerDTO);
  }

  public ExternalTaskInstanceResponder respondToExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return client.respondToExternalTask(processInstanceId, elementInstanceIdPath);
  }

  public UserTaskInstanceResponder completeUserTask(UserTaskTriggerDTO userTaskTriggerDTO) {
    return client.completeUserTask(userTaskTriggerDTO);
  }

  public void completeUserTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    client.completeUserTask(processInstanceId, elementInstanceIdPath, variables);
  }

  public void completeUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.completeUserTask(processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    client.errorUserTask(processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.errorUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    client.escalateUserTask(processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.escalateUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  public void completeExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    client.completeExternalTask(processInstanceId, elementInstanceIdPath, variables);
  }

  public void completeExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.completeExternalTask(
        processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    client.errorExternalTask(processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.errorExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    client.escalateExternalTask(processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.escalateExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  public void setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    client.setVariable(processInstanceId, elementInstanceIdPath, variables);
  }

  public void setVariable(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    client.setVariable(processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  public void abortElementInstance(UUID processInstanceId) {
    client.abortElementInstance(processInstanceId);
  }

  public void abortElementInstance(UUID activeProcessInstanceId, List<Long> elementInstanceIdPath) {
    client.abortElementInstance(activeProcessInstanceId, elementInstanceIdPath);
  }

  public void abortElementInstance(
      UUID activeProcessInstanceId,
      List<Long> elementInstanceIdPath,
      @Nullable String authorizationToken) {
    client.abortElementInstance(activeProcessInstanceId, elementInstanceIdPath, authorizationToken);
  }

  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey) throws IOException {
    return client.getProcessDefinitionXml(processDefinitionKey);
  }

  public String getDmnDefinitionXml(DmnDefinitionKey dmnDefinitionKey) throws IOException {
    return client.getDmnDefinitionXml(dmnDefinitionKey);
  }

  public Optional<DmnDefinitionKey> getDmnDefinitionKeyForDecision(String decisionId) {
    return client.getDmnDefinitionKeyForDecision(decisionId);
  }

  public java.util.Map<String, DmnDefinitionKey> getDmnDecisionIndex() {
    return client.getDmnDecisionIndex();
  }
}

