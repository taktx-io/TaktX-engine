/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.SignalSerializer;
import io.taktx.dto.SignalDTO;
import io.taktx.util.TaktPropertiesHelper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * A sender for message events, responsible for producing and sending MessageEventDTO objects to a
 * Kafka topic.
 */
public class SignalSender {

  private final KafkaProducer<String, SignalDTO> signalEmitter;
  private final TaktPropertiesHelper taktPropertiesHelper;

  /**
   * Constructor for MessageEventSender.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public SignalSender(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.signalEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, SignalSerializer.class));
  }

  /**
   * Sends a signal to the configured Kafka topic.
   *
   * @param signalDTO the SignalDTO to send
   */
  public void sendMSignal(SignalDTO signalDTO) {
    signalEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNAL_TOPIC.getTopicName()),
            signalDTO.getSignalName(),
            signalDTO));
  }
}
