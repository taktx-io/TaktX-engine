/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
            taktPropertiesHelper.getKafkaProducerProperties(),
            new StringSerializer(),
            new SignalSerializer());
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
