/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TopicsTest {

  @Test
  void allTopics_haveNonBlankTopicName() {
    for (Topics topic : Topics.values()) {
      assertThat(topic.getTopicName()).as("topicName for %s", topic).isNotBlank();
    }
  }

  @Test
  void initialFixedTopics_containsOnlyInitialAvailableTopics() {
    Set<Topics> initial = Topics.initialFixedTopics();
    assertThat(initial).isNotEmpty().allMatch(Topics::isInitialAvailable);
  }

  @Test
  void managedFixedTopics_containsOnlyNonInitialTopics() {
    Set<Topics> managed = Topics.managedFixedTopics();
    assertThat(managed).isNotEmpty().noneMatch(Topics::isInitialAvailable);
  }

  @Test
  void initialAndManagedTopics_togetherCoverAllTopics() {
    Set<Topics> initial = Topics.initialFixedTopics();
    Set<Topics> managed = Topics.managedFixedTopics();
    assertThat(initial.size() + managed.size()).isEqualTo(Topics.values().length);
  }

  @Test
  void specificTopicNames_areAsExpected() {
    assertThat(Topics.DMN_DEFINITIONS_TRIGGER_TOPIC.getTopicName()).isEqualTo("dmn-definitions");
    assertThat(Topics.XML_BY_DMN_DEFINITION_ID.getTopicName())
        .isEqualTo("xml-by-dmn-definition-id");
    assertThat(Topics.XML_BY_PROCESS_DEFINITION_ID.getTopicName())
        .isEqualTo("xml-by-process-definition-id");
    assertThat(Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()).isEqualTo("definitions");
    // Unified DLQ topics — single namespace-scoped set
    assertThat(Topics.DLQ.getTopicName()).isEqualTo("dlq");
    assertThat(Topics.DLQ_REPLAY.getTopicName()).isEqualTo("dlq.replay");
    assertThat(Topics.DLQ_REPLAY_RESULTS.getTopicName()).isEqualTo("dlq.replay-results");
  }

  @Test
  void dlqTopics_allHaveDeleteCleanupPolicy() {
    for (Topics topic : Topics.values()) {
      if (topic.getTopicName().equals("dlq") || topic.getTopicName().startsWith("dlq.")) {
        assertThat(topic.getCleanupPolicy())
            .as("DLQ topic %s must use DELETE cleanup policy", topic.getTopicName())
            .isEqualTo(CleanupPolicy.DELETE);
      }
    }
  }
}
