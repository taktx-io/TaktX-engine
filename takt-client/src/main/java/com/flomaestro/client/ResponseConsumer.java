package com.flomaestro.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ResponseConsumer {

  private final KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter;
  private final ExternalTaskTriggerDTO externalTaskTrigger;
  private final String topicName;
  private final ObjectMapper objectMapper;
  @Getter private boolean asParameter;

  public ResponseConsumer(
      KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter,
      ExternalTaskTriggerDTO externalTaskTrigger,
      String topicName,
      ObjectMapper objectMapper) {
    this.responseEmitter = responseEmitter;
    this.externalTaskTrigger = externalTaskTrigger;
    this.topicName = topicName;
    this.objectMapper = objectMapper;
    this.asParameter = false;
  }

  public void respondSucess(Object variable) {
    Map<String, JsonNode> variablesMap =
        variable == null ? Map.of() : objectMapper.convertValue(variable, LinkedHashMap.class);

    ExternalTaskResponseResultDTO externalTaskResponseResult =
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.SUCCESS,
            true,
            Constants.NONE,
            Constants.NONE,
            Constants.NONE,
            0L);
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementInstanceIdPath(),
            externalTaskResponseResult,
            new VariablesDTO(variablesMap));
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void respondEscalation(EscalationEventException escalationEvent) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementInstanceIdPath(),
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
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementInstanceIdPath(),
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
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.PROMISE,
                true,
                Constants.NONE,
                Constants.NONE,
                Constants.NONE,
                duration.toMillis()),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            topicName, processInstanceTrigger.getProcessInstanceKey(), processInstanceTrigger));
  }

  public void setAsParameter() {
    this.asParameter = true;
  }
}
