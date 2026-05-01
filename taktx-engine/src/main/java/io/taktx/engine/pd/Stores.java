/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.Topics;
import lombok.Getter;

@Getter
public enum Stores {
  SCHEDULES_MINUTE("schedules-minute"),
  SCHEDULES_HOURLY("schedules-hourly"),
  SCHEDULES_DAILY("schedules-daily"),
  SCHEDULES_WEEKLY("schedules-weekly"),
  SCHEDULES_YEARLY("schedules-yearly"),
  PROCESS_INSTANCE("process-instance"),
  FLOW_NODE_INSTANCE("flownode-instance"),
  PROCESS_INSTANCE_DEFINITION("process-instance-definition"),
  DEFINITION_COUNT_BY_ID("definition-count-by-id"),
  XML_BY_PROCESS_DEFINITION_ID("xml-by-process-definition-id"),
  GLOBAL_PROCESS_DEFINITION("global-process-definition"),
  TOPIC_META_REQUESTED(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName()),
  TOPIC_META_ACTUAL(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName()),
  DEFINITION_MESSAGE_SUBSCRIPTION("definition-message-subscription"),
  CORRELATION_MESSAGE_SUBSCRIPTION("correlation-message-subscription"),
  VARIABLES("variables"),
  VERSION_BY_HASH("version-by-hash"),
  INSTANCE_SIGNAL_SUBSCRIPTIONS("instance-signal-subscriptions"),
  DEFINITION_SIGNAL_SUBSCRIPTIONS("definition-signal-subscriptions"),
  GLOBAL_CONFIGURATION(Topics.CONFIGURATION_TOPIC.getTopicName()),
  SIGNING_KEYS(Topics.SIGNING_KEYS_TOPIC.getTopicName()),
  REPLAY_PROTECTION("replay-protection"),
  GLOBAL_DMN_DEFINITION("global-dmn-definition"),
  DMN_VERSION_BY_HASH("dmn-version-by-hash"),
  XML_BY_DMN_DEFINITION_ID(Topics.XML_BY_DMN_DEFINITION_ID.getTopicName());

  private final String storename;

  Stores(String storename) {
    this.storename = storename;
  }
}
