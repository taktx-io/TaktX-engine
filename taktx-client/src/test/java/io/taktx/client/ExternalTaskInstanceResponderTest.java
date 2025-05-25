package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.VariablesDTO;
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
  private KafkaProducer<UUID, ContinueFlowElementTriggerDTO> mockProducer;
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
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ContinueFlowElementTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
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
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ContinueFlowElementTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
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
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertSuccessResult(resultDTO);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondEscalation() {
    // Given
    String message = "escalation-message";
    String code = "ESC-001";

    // When
    responder.respondEscalation(code, message);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertEscalationResult(resultDTO, code, message);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondError() {
    // Given
    boolean allowRetry = true;
    String code = "ERR-001";
    String message = "error-message";

    // When
    responder.respondError(allowRetry, code, message);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertErrorResult(resultDTO, allowRetry, code, message);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondErrorWithVariables() {
    // Given
    boolean allowRetry = false;
    String code = "ERR-002";
    String message = "error-message-2";
    VariablesDTO variables = VariablesDTO.of("errorKey", "errorValue");

    // When
    responder.respondError(allowRetry, code, message, variables);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO =
        assertRecordBasics(externalTaskResponseTriggerRecord);

    assertTriggerBasics(externalTaskResponseTriggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertErrorResult(resultDTO, allowRetry, code, message);
  }

  @SuppressWarnings("unchecked")
  @Test
  void respondPromise() {
    // Given
    Duration duration = Duration.ofMinutes(5);

    // When
    responder.respondPromise(duration);

    // Then
    ArgumentCaptor<ProducerRecord<UUID, ContinueFlowElementTriggerDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockProducer).send(captor.capture());

    ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord =
        captor.getValue();
    assertRecordBasics(externalTaskResponseTriggerRecord);

    ContinueFlowElementTriggerDTO triggerDTO = externalTaskResponseTriggerRecord.value();
    ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO = assertTriggerBasics(triggerDTO);

    ExternalTaskResponseResultDTO resultDTO =
        externalTaskResponseTriggerDTO.getExternalTaskResponseResult();
    assertPromiseResult(resultDTO, duration);
  }

  // Helper assertion methods
  private ExternalTaskResponseTriggerDTO assertRecordBasics(
      ProducerRecord<UUID, ContinueFlowElementTriggerDTO> externalTaskResponseTriggerRecord) {
    assertThat(externalTaskResponseTriggerRecord.topic()).isEqualTo(topicName);
    assertThat(externalTaskResponseTriggerRecord.key()).isEqualTo(processInstanceKey);
    assertThat(externalTaskResponseTriggerRecord.value())
        .isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    return (ExternalTaskResponseTriggerDTO) externalTaskResponseTriggerRecord.value();
  }

  private ExternalTaskResponseTriggerDTO assertTriggerBasics(
      ContinueFlowElementTriggerDTO triggerDTO) {
    assertThat(triggerDTO.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(triggerDTO.getElementInstanceIdPath()).isEqualTo(elementInstanceIdPath);
    assertThat(triggerDTO).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    return (ExternalTaskResponseTriggerDTO) triggerDTO;
  }

  private void assertSuccessResult(ExternalTaskResponseResultDTO resultDTO) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.SUCCESS);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isNull();
    assertThat(resultDTO.getCode()).isNull();
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertEscalationResult(
      ExternalTaskResponseResultDTO resultDTO, String code, String message) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.ESCALATION);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isEqualTo(message);
    assertThat(resultDTO.getCode()).isEqualTo(code);
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertErrorResult(
      ExternalTaskResponseResultDTO resultDTO, boolean allowRetry, String code, String message) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.ERROR);
    assertThat(resultDTO.getAllowRetry()).isEqualTo(allowRetry);
    assertThat(resultDTO.getMessage()).isEqualTo(message);
    assertThat(resultDTO.getCode()).isEqualTo(code);
    assertThat(resultDTO.getTimeout()).isZero();
  }

  private void assertPromiseResult(ExternalTaskResponseResultDTO resultDTO, Duration duration) {
    assertThat(resultDTO.getResponseType()).isEqualTo(ExternalTaskResponseType.PROMISE);
    assertThat(resultDTO.getAllowRetry()).isTrue();
    assertThat(resultDTO.getMessage()).isNull();
    assertThat(resultDTO.getCode()).isNull();
    assertThat(resultDTO.getTimeout()).isEqualTo(duration.toMillis());
  }

  @Data
  static class TestDto {
    private String value;
  }
}
