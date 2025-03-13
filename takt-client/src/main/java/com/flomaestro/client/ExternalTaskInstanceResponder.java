package com.flomaestro.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
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
        variable == null ? Map.of() : OBJECT_MAPPER.convertValue(variable, LinkedHashMap.class);
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

  public void respondEscalation(EscalationEventException escalationEvent) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ESCALATION,
                true,
                escalationEvent.getName(),
                escalationEvent.getMessage(),
                escalationEvent.getCode(),
                0L),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondError(boolean allowRetry, String code, String name, String message) {

    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            processInstanceKey,
            elementInstanceIdPath,
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ESCALATION, allowRetry, code, name, message, 0L),
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
                ExternalTaskResponseType.PROMISE, true, null, null, null, duration.toMillis()),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }
}
