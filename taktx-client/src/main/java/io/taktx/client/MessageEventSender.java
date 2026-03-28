/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.MessageEventKeySerializer;
import io.taktx.client.serdes.MessageEventSerializer;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.util.TaktPropertiesHelper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * A sender for message events, responsible for producing and sending MessageEventDTO objects to a
 * Kafka topic.
 */
public class MessageEventSender {

  private final KafkaProducer<MessageEventKeyDTO, MessageEventDTO> messageEventEmitter;
  private final TaktPropertiesHelper taktPropertiesHelper;

  /**
   * Constructor for MessageEventSender.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public MessageEventSender(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.messageEventEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(),
            new MessageEventKeySerializer(),
            new MessageEventSerializer());
  }

  /**
   * Sends a message event to the configured Kafka topic.
   *
   * @param messageEventDTO the MessageEventDTO to send
   */
  public void sendMessage(MessageEventDTO messageEventDTO) {
    messageEventEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
            messageEventDTO.toMessageEventKey(),
            messageEventDTO));
  }
}
