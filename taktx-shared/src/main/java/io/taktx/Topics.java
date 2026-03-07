/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum Topics {
  TOPIC_META_REQUESTED_TOPIC("topic-meta-requested", true, CleanupPolicy.COMPACT),
  TOPIC_META_ACTUAL_TOPIC("topic-meta-actual", true, CleanupPolicy.COMPACT),
  CONFIGURATION_TOPIC("taktx-configuration", true, CleanupPolicy.COMPACT),
  SIGNING_KEYS_TOPIC("taktx-signing-keys", true, CleanupPolicy.COMPACT),
  XML_BY_PROCESS_DEFINITION_ID("xml-by-process-definition-id", false, CleanupPolicy.COMPACT),
  PROCESS_DEFINITION_ACTIVATION_TOPIC(
      "process-definition-activation", false, CleanupPolicy.COMPACT),
  MESSAGE_EVENT_TOPIC("message-event", false, CleanupPolicy.DELETE),
  SCHEDULE_COMMANDS("schedule-commands", false, CleanupPolicy.DELETE),
  INSTANCE_UPDATE_TOPIC("instance-update", false, CleanupPolicy.DELETE),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance", false, CleanupPolicy.DELETE),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions", false, CleanupPolicy.DELETE),
  SIGNAL_TOPIC("signals", false, CleanupPolicy.COMPACT),
  USER_TASK_TRIGGER_TOPIC("usertasks", false, CleanupPolicy.COMPACT),
  USER_TASK_RESPONSE_TOPIC("usertasks-response", false, CleanupPolicy.DELETE);

  private final String topicName;
  private final boolean initialAvailable;
  private final CleanupPolicy cleanupPolicy;

  Topics(String topicName, boolean initialAvailable, CleanupPolicy cleanupPolicy) {
    this.topicName = topicName;
    this.initialAvailable = initialAvailable;
    this.cleanupPolicy = cleanupPolicy;
  }

  public static Set<Topics> initialFixedTopics() {
    return Arrays.stream(values()).filter(t -> t.initialAvailable).collect(Collectors.toSet());
  }

  public static Set<Topics> managedFixedTopics() {
    return Arrays.stream(values()).filter(t -> !t.initialAvailable).collect(Collectors.toSet());
  }
}
