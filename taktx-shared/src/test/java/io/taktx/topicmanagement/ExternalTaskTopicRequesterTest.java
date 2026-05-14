/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.topicmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.Topics;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExternalTaskTopicRequesterTest {

  @SuppressWarnings("unchecked")
  @Test
  void requestExternalTaskTopic_autoPopulatesMessageId() {
    KafkaProducer<String, TopicMetaDTO> producer = mock(KafkaProducer.class);
    ExternalTaskTopicRequester requester =
        new ExternalTaskTopicRequester(taktPropertiesHelper(), producer);

    String topicName =
        requester.requestExternalTaskTopic(
            "ship-order", 3, io.taktx.CleanupPolicy.DELETE, (short) 1);

    ArgumentCaptor<ProducerRecord<String, TopicMetaDTO>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());

    ProducerRecord<String, TopicMetaDTO> produced = captor.getValue();
    assertThat(topicName).isEqualTo("tenant.ns.external-task-trigger-ship-order");
    assertThat(produced.topic())
        .isEqualTo("tenant.ns." + Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName());
    assertThat(produced.key()).isEqualTo(topicName);
    assertThat(produced.value().getTopicName()).isEqualTo(topicName);
    assertThat(produced.value().getMessageId()).isNotBlank();
  }

  private static TaktPropertiesHelper taktPropertiesHelper() {
    Properties props = new Properties();
    props.setProperty("taktx.engine.tenant-id", "tenant");
    props.setProperty("taktx.engine.namespace", "ns");
    return new TaktPropertiesHelper(props);
  }
}
