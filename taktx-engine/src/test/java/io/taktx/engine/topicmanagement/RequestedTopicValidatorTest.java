/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.CleanupPolicy;
import io.taktx.dto.Constants;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestedTopicValidatorTest {

  private static final String LOCAL_PREFIX = "acme.prod.";
  private static final String ALLOWED_PREFIX =
      LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX;

  private RequestedTopicValidator validator;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed("")).thenReturn(LOCAL_PREFIX);
    when(taktConfiguration.getPrefixed(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX))
        .thenReturn(ALLOWED_PREFIX);
    validator = new RequestedTopicValidator(taktConfiguration);
  }

  @Test
  void acceptsLocalExternalTaskTriggerTopic() {
    String topicName = ALLOWED_PREFIX + "payment-worker";

    RequestedTopicValidationResult result =
        validator.validate(
            topicName, new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1));

    assertThat(result.valid()).isTrue();
    assertThat(result.topicName()).isEqualTo(topicName);
    assertThat(result.rejectionReason()).isNull();
    assertThat(validator.isAllowedRequestedTopicName(topicName)).isTrue();
  }

  @Test
  void rejectsNonLocalPrefix() {
    String topicName = "other.prod.external-task-trigger-payment-worker";

    RequestedTopicValidationResult result =
        validator.validate(
            topicName, new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1));

    assertThat(result.valid()).isFalse();
    assertThat(result.rejectionReason()).contains("local tenant/namespace prefix");
  }

  @Test
  void rejectsManagedFixedTopicRequest() {
    String topicName = LOCAL_PREFIX + "process-instance";

    RequestedTopicValidationResult result =
        validator.validate(
            topicName, new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1));

    assertThat(result.valid()).isFalse();
    assertThat(result.rejectionReason())
        .contains("only local external-task trigger topics may be created");
    assertThat(validator.isAllowedRequestedTopicName(topicName)).isFalse();
  }

  @Test
  void rejectsBlankExternalTaskSuffix() {
    String topicName = ALLOWED_PREFIX;

    RequestedTopicValidationResult result =
        validator.validate(
            topicName, new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1));

    assertThat(result.valid()).isFalse();
    assertThat(result.rejectionReason()).contains("suffix must not be blank");
    assertThat(validator.isAllowedRequestedTopicName(topicName)).isFalse();
  }

  @Test
  void rejectsRecordKeyAndPayloadTopicMismatch() {
    String recordKey = ALLOWED_PREFIX + "worker-a";
    String payloadTopicName = ALLOWED_PREFIX + "worker-b";

    RequestedTopicValidationResult result =
        validator.validate(
            recordKey, new TopicMetaDTO(payloadTopicName, 3, CleanupPolicy.DELETE, (short) 1));

    assertThat(result.valid()).isFalse();
    assertThat(result.topicName()).isEqualTo(recordKey);
    assertThat(result.rejectionReason()).contains("must exactly match");
  }
}
