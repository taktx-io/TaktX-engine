package com.flomaestro.client;

import com.flomaestro.client.serdes.MessageEventKeySerializer;
import com.flomaestro.client.serdes.MessageEventSerializer;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class MessageEventSender {

  private final KafkaProducer<MessageEventKeyDTO, MessageEventDTO> messageEventEmitter;
  private final TaktPropertiesHelper taktPropertiesHelper;

  public MessageEventSender(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.messageEventEmitter =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                MessageEventKeySerializer.class, MessageEventSerializer.class));
  }

  public void sendMessage(MessageEventDTO messageEventDTO) {
    messageEventEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.MESSAGE_EVENT_TOPIC.getTopicName()),
            messageEventDTO.toMessageEventKey(),
            messageEventDTO));
  }
}
