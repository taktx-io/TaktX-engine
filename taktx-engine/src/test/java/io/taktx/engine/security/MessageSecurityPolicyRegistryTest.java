/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.Topics;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageSecurityPolicyRegistryTest {

  private final MessageSecurityPolicyRegistry registry = new MessageSecurityPolicyRegistry();

  @Test
  void processInstancePolicies_resolveDeterministically_forProtectedMessageTypes() {
    String processInstanceTopic = Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();
    Map<Class<?>, KeyRole> expectedRoles =
        Map.of(
            StartCommandDTO.class, KeyRole.CLIENT,
            AbortTriggerDTO.class, KeyRole.CLIENT,
            SetVariableTriggerDTO.class, KeyRole.CLIENT,
            ExternalTaskResponseTriggerDTO.class, KeyRole.CLIENT,
            UserTaskResponseTriggerDTO.class, KeyRole.CLIENT,
            ContinueFlowElementTriggerDTO.class, KeyRole.ENGINE,
            EventSignalTriggerDTO.class, KeyRole.ENGINE,
            StartFlowElementTriggerDTO.class, KeyRole.ENGINE);

    expectedRoles.forEach(
        (messageClass, expectedRole) -> {
          MessageSecurityPolicy policy = registry.resolve(processInstanceTopic, messageClass);
          assertThat(policy.topicName()).isEqualTo(processInstanceTopic);
          assertThat(policy.messageClass()).isEqualTo(messageClass);
          assertThat(policy.requireSignature()).isTrue();
          assertThat(policy.minimumAllowedRole()).isEqualTo(expectedRole);
        });
  }

  @Test
  void entryCommandPolicies_requireReplayAndTrustedClientOrHigherSignatures() {
    String processInstanceTopic = Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();

    MessageSecurityPolicy startPolicy =
        registry.resolve(processInstanceTopic, StartCommandDTO.class);
    MessageSecurityPolicy abortPolicy =
        registry.resolve(processInstanceTopic, AbortTriggerDTO.class);
    MessageSecurityPolicy setVariablePolicy =
        registry.resolve(processInstanceTopic, SetVariableTriggerDTO.class);

    assertEntryCommandPolicy(startPolicy, StartCommandDTO.class);
    assertEntryCommandPolicy(abortPolicy, AbortTriggerDTO.class);
    assertEntryCommandPolicy(setVariablePolicy, SetVariableTriggerDTO.class);
  }

  @Test
  void taskCompletionPolicies_requireReplayAndTrustedClientOrHigherSignatures() {
    String processInstanceTopic = Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();

    MessageSecurityPolicy externalTaskPolicy =
        registry.resolve(processInstanceTopic, ExternalTaskResponseTriggerDTO.class);
    MessageSecurityPolicy userTaskPolicy =
        registry.resolve(processInstanceTopic, UserTaskResponseTriggerDTO.class);

    assertTaskCompletionPolicy(externalTaskPolicy, ExternalTaskResponseTriggerDTO.class);
    assertTaskCompletionPolicy(userTaskPolicy, UserTaskResponseTriggerDTO.class);
  }

  @Test
  void controlPlanePolicies_requireExpectedRoles() {
    MessageSecurityPolicy schedulePolicy =
        registry.resolve(Topics.SCHEDULE_COMMANDS.getTopicName(), MessageScheduleDTO.class);
    assertThat(schedulePolicy.requireSignature()).isTrue();
    assertThat(schedulePolicy.requireReplay()).isFalse();
    assertThat(schedulePolicy.minimumAllowedRole()).isEqualTo(KeyRole.ENGINE);

    MessageSecurityPolicy topicMetaPolicy =
        registry.resolve(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName(), TopicMetaDTO.class);
    assertThat(topicMetaPolicy.requireSignature()).isTrue();
    assertThat(topicMetaPolicy.requireReplay()).isFalse();
    assertThat(topicMetaPolicy.minimumAllowedRole()).isEqualTo(KeyRole.CLIENT);
  }

  @Test
  void externalMessageAndSignalIngressPolicies_requireTrustedClientSignatures() {
    MessageSecurityPolicy definitionMessagePolicy =
        registry.resolve(
            Topics.MESSAGE_EVENT_TOPIC.getTopicName(), DefinitionMessageEventTriggerDTO.class);
    MessageSecurityPolicy correlationMessagePolicy =
        registry.resolve(
            Topics.MESSAGE_EVENT_TOPIC.getTopicName(), CorrelationMessageEventTriggerDTO.class);
    MessageSecurityPolicy signalPolicy =
        registry.resolve(Topics.SIGNAL_TOPIC.getTopicName(), SignalDTO.class);

    assertClientIngressPolicy(definitionMessagePolicy, DefinitionMessageEventTriggerDTO.class);
    assertClientIngressPolicy(correlationMessagePolicy, CorrelationMessageEventTriggerDTO.class);
    assertClientIngressPolicy(signalPolicy, SignalDTO.class);
  }

  @Test
  void unknownTopicAndMessageCombination_failsClosed() {
    assertThatThrownBy(() -> registry.resolve("tenant.ns.unknown-topic", TopicMetaDTO.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No message security policy registered");

    String topicName = Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();
    assertThatThrownBy(() -> registry.resolve(topicName, UnsupportedTrigger.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UnsupportedTrigger");
  }

  private static void assertEntryCommandPolicy(
      MessageSecurityPolicy policy, Class<?> expectedMessageClass) {
    assertThat(policy.messageClass()).isEqualTo(expectedMessageClass);
    assertThat(policy.requireSignature()).isTrue();
    assertThat(policy.requireReplay()).isTrue();
    assertThat(policy.minimumAllowedRole()).isEqualTo(KeyRole.CLIENT);
  }

  private static void assertTaskCompletionPolicy(
      MessageSecurityPolicy policy, Class<?> expectedMessageClass) {
    assertThat(policy.messageClass()).isEqualTo(expectedMessageClass);
    assertThat(policy.requireSignature()).isTrue();
    assertThat(policy.requireReplay()).isTrue();
    assertThat(policy.minimumAllowedRole()).isEqualTo(KeyRole.CLIENT);
  }

  private static void assertClientIngressPolicy(
      MessageSecurityPolicy policy, Class<?> expectedMessageClass) {
    assertThat(policy.messageClass()).isEqualTo(expectedMessageClass);
    assertThat(policy.requireSignature()).isTrue();
    assertThat(policy.requireReplay()).isFalse();
    assertThat(policy.minimumAllowedRole()).isEqualTo(KeyRole.CLIENT);
  }

  private static final class UnsupportedTrigger extends io.taktx.dto.ProcessInstanceTriggerDTO {
    private UnsupportedTrigger() {
      super(null);
    }
  }
}
