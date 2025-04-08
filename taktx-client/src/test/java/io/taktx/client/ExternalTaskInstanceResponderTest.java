package io.taktx.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import io.taktx.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.v_1_0_0.ExternalTaskResponseType;
import io.taktx.dto.v_1_0_0.VariablesDTO;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExternalTaskInstanceResponderTest {
  private KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> mockProducer;
  private ExternalTaskInstanceResponder responder;
  private UUID processInstanceKey;
  private List<Long> elementInstanceIdPath;
  private String topicName;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    mockProducer = mock(KafkaProducer.class);
    processInstanceKey = UUID.randomUUID();
    elementInstanceIdPath = List.of(1001L, 1002L);
    topicName = "test-topic";
    responder =
        new ExternalTaskInstanceResponder(
            mockProducer, topicName, processInstanceKey, elementInstanceIdPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithNoVariables() {
    // When
    responder.respondSuccess();

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithCustomObject() {
    // Given
    TestDto testDto = new TestDto();
    testDto.setValue("test-value");

    // When
    responder.respondSuccess(testDto);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondSuccessWithVariablesMap() {
    // Given
    Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    variables.put("key2", 42);

    // When
    responder.respondSuccess(variables);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondEscalation() {
    // Given
    String name = "escalation-name";
    String message = "escalation-message";
    String code = "ESC-001";

    // When
    responder.respondEscalation(name, message, code);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertEscalationResult(resultDTO, name, message, code);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondError() {
    // Given
    boolean allowRetry = true;
    String code = "ERR-001";
    String name = "error-name";
    String message = "error-message";

    // When
    responder.respondError(allowRetry, code, name, message);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertErrorResult(resultDTO, allowRetry, name, message, code);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondErrorWithVariables() {
    // Given
    boolean allowRetry = false;
    String code = "ERR-002";
    String name = "error-name-2";
    String message = "error-message-2";
    VariablesDTO variables = VariablesDTO.of("errorKey", "errorValue");

    // When
    responder.respondError(allowRetry, code, name, message, variables);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertErrorResult(resultDTO, allowRetry, name, message, code);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondPromise() {
    // Given
    Duration duration = Duration.ofMinutes(5);

    // When
    responder.respondPromise(duration);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ExternalTaskResponseTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ExternalTaskResponseTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO = triggerDTO.getExternalTaskResponseResult();
    assertPromiseResult(resultDTO, duration);
  }

  // Helper assertion methods
  private void assertRecordBasics(
      ProducerRecord<UUID, ExternalTaskResponseTriggerDTO> externalTaskResponseTriggerRecord) {
    org.assertj.core.api.Assertions.assertThat(externalTaskResponseTriggerRecord.topic())
        .isEqualTo(topicName);
    org.assertj.core.api.Assertions.assertThat(externalTaskResponseTriggerRecord.key())
        .isEqualTo(processInstanceKey);
  }

  private void assertTriggerBasics(ExternalTaskResponseTriggerDTO triggerDTO) {
    org.assertj.core.api.Assertions.assertThat(triggerDTO.getProcessInstanceKey())
        .isEqualTo(processInstanceKey);
    org.assertj.core.api.Assertions.assertThat(triggerDTO.getElementInstanceIdPath())
        .isEqualTo(elementInstanceIdPath);
  }

  private void assertSuccessResult(ExternalTaskResponseResultDTO resultDTO) {
    org.assertj.core.api.Assertions.assertThat(resultDTO.getResponseType())
        .isEqualTo(ExternalTaskResponseType.SUCCESS);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getAllowRetry()).isTrue();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getName()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getMessage()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getCode()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertEscalationResult(
      ExternalTaskResponseResultDTO resultDTO, String name, String message, String code) {
    org.assertj.core.api.Assertions.assertThat(resultDTO.getResponseType())
        .isEqualTo(ExternalTaskResponseType.ESCALATION);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getAllowRetry()).isTrue();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getName()).isEqualTo(name);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getMessage()).isEqualTo(message);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getCode()).isEqualTo(code);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertErrorResult(
      ExternalTaskResponseResultDTO resultDTO,
      boolean allowRetry,
      String name,
      String message,
      String code) {
    org.assertj.core.api.Assertions.assertThat(resultDTO.getResponseType())
        .isEqualTo(ExternalTaskResponseType.ERROR);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getAllowRetry()).isEqualTo(allowRetry);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getName()).isEqualTo(name);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getMessage()).isEqualTo(message);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getCode()).isEqualTo(code);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertPromiseResult(ExternalTaskResponseResultDTO resultDTO, Duration duration) {
    org.assertj.core.api.Assertions.assertThat(resultDTO.getResponseType())
        .isEqualTo(ExternalTaskResponseType.PROMISE);
    org.assertj.core.api.Assertions.assertThat(resultDTO.getAllowRetry()).isTrue();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getName()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getMessage()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getCode()).isNull();
    org.assertj.core.api.Assertions.assertThat(resultDTO.getTimeout())
        .isEqualTo(duration.toMillis());
  }

  @Data
  static class TestDto {
    private String value;
  }
}
