/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.VariablesDTO;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ExternalTaskInstanceResponder {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private final KafkaProducer<UUID, ContinueFlowElementTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceKey;
  private final List<Long> elementInstanceIdPath;

  public ExternalTaskInstanceResponder(
      KafkaProducer<UUID, ContinueFlowElementTriggerDTO> responseEmitter,
      String topicName,
      UUID processInstanceKey,
      List<Long> elementInstanceIdPath) {
    this.responseEmitter = responseEmitter;
    this.topicName = topicName;
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }

  public void respondSuccess() {
    Map<String, JsonNode> variablesMap = new HashMap<>();
    respondSuccess(variablesMap);
  }

  public void respondSuccess(Object variable) {
    Map<String, JsonNode> variablesMap =
        variable == null
            ? Map.of()
            : OBJECT_MAPPER.convertValue(
                variable, new TypeReference<LinkedHashMap<String, JsonNode>>() {});
    respondSuccess(variablesMap);
  }

  public void respondSuccess(Map<String, JsonNode> variablesMap) {
    ExternalTaskResponseResultDTO externalTaskResponseResult =
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L);
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            externalTaskResponseResult,
            new VariablesDTO(variablesMap));
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondEscalation(String code, String message) {
    respondEscalation(code, message, VariablesDTO.empty());
  }

  public void respondEscalation(String code, String message, VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ESCALATION, true, code, message, 0L),
            variables);
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondError(
      boolean allowRetry, String code, String message) {

    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ERROR, allowRetry, code, message, 0L),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondPromise(Duration duration) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.PROMISE, true, null, null, duration.toMillis()),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }
}
