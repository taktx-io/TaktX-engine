/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
  VERSION_BY_HASH("version-by-hash");

  private final String storename;

  Stores(String storename) {
    this.storename = storename;
  }
}
