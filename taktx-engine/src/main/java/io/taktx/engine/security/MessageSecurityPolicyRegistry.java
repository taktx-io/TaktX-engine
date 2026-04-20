/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.Topics;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

/** Central registry of protected (topic, DTO/message) security policies. */
@ApplicationScoped
public class MessageSecurityPolicyRegistry {

  private static final String PROCESS_INSTANCE_TOPIC =
      Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();
  private static final String SCHEDULE_COMMANDS_TOPIC = Topics.SCHEDULE_COMMANDS.getTopicName();
  private static final String TOPIC_META_REQUESTED_TOPIC =
      Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName();

  private final Map<PolicyKey, MessageSecurityPolicy> policies =
      Map.ofEntries(
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, StartCommandDTO.class),
              entryCommandPolicy(StartCommandDTO.class)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, AbortTriggerDTO.class),
              entryCommandPolicy(AbortTriggerDTO.class)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, SetVariableTriggerDTO.class),
              entryCommandPolicy(SetVariableTriggerDTO.class)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, ExternalTaskResponseTriggerDTO.class),
              signedProcessInstancePolicy(ExternalTaskResponseTriggerDTO.class, KeyRole.CLIENT)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, UserTaskResponseTriggerDTO.class),
              signedProcessInstancePolicy(UserTaskResponseTriggerDTO.class, KeyRole.CLIENT)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, ContinueFlowElementTriggerDTO.class),
              signedProcessInstancePolicy(ContinueFlowElementTriggerDTO.class, KeyRole.ENGINE)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, EventSignalTriggerDTO.class),
              signedProcessInstancePolicy(EventSignalTriggerDTO.class, KeyRole.ENGINE)),
          Map.entry(
              new PolicyKey(PROCESS_INSTANCE_TOPIC, StartFlowElementTriggerDTO.class),
              signedProcessInstancePolicy(StartFlowElementTriggerDTO.class, KeyRole.ENGINE)),
          Map.entry(
              new PolicyKey(SCHEDULE_COMMANDS_TOPIC, MessageScheduleDTO.class),
              MessageSecurityPolicy.builder(SCHEDULE_COMMANDS_TOPIC, MessageScheduleDTO.class)
                  .allowedRoles(Set.of(KeyRole.ENGINE))
                  .requireSignature(true)
                  .build()),
          Map.entry(
              new PolicyKey(TOPIC_META_REQUESTED_TOPIC, TopicMetaDTO.class),
              MessageSecurityPolicy.builder(TOPIC_META_REQUESTED_TOPIC, TopicMetaDTO.class)
                  .allowedRoles(Set.of(KeyRole.CLIENT))
                  .requireSignature(true)
                  .build()));

  public MessageSecurityPolicy resolve(String topicName, Class<?> messageClass) {
    MessageSecurityPolicy policy = policies.get(new PolicyKey(topicName, messageClass));
    if (policy == null) {
      throw new IllegalStateException(
          "No message security policy registered for topic='"
              + topicName
              + "' messageClass='"
              + (messageClass == null ? null : messageClass.getName())
              + "'");
    }
    return policy;
  }

  private static MessageSecurityPolicy entryCommandPolicy(Class<?> messageClass) {
    return MessageSecurityPolicy.builder(PROCESS_INSTANCE_TOPIC, messageClass)
        .allowedRoles(Set.of(KeyRole.CLIENT))
        .requireSignature(true)
        .requireReplay(true)
        .requireJwt(true)
        .allowEngineSignatureAsJwtEquivalent(true)
        .build();
  }

  private static MessageSecurityPolicy signedProcessInstancePolicy(
      Class<?> messageClass, KeyRole minimumRole) {
    return MessageSecurityPolicy.builder(PROCESS_INSTANCE_TOPIC, messageClass)
        .allowedRoles(Set.of(minimumRole))
        .requireSignature(true)
        .build();
  }

  private record PolicyKey(String topicName, Class<?> messageClass) {}
}
