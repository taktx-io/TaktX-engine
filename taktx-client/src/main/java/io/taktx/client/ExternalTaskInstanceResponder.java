/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
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
  private final KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter;
  private final String topicName;
  private final UUID processInstanceKey;
  private final List<Long> elementInstanceIdPath;

  public ExternalTaskInstanceResponder(
      KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter,
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
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.SUCCESS, true, null, null, null, 0L);
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

  public void respondEscalation(String name, String message, String code) {
    respondEscalation(name, message, code, VariablesDTO.empty());
  }

  public void respondEscalation(String name, String message, String code, VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ESCALATION, true, name, message, code, 0L),
            variables);
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondError(
      boolean allowRetry, String code, String name, String message, VariablesDTO variables) {

    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ERROR, allowRetry, name, message, code, 0L),
            variables);
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondError(boolean allowRetry, String code, String name, String message) {
    respondError(allowRetry, code, name, message, VariablesDTO.empty());
  }

  public void respondPromise(Duration duration) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.PROMISE, true, null, null, null, duration.toMillis()),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }
}
