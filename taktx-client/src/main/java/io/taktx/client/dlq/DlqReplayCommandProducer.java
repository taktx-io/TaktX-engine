/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.Topics;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.serdes.DlqProtoMapper;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes {@link DlqReplayCommand} records to the {@code dlq.replay} topic.
 *
 * <p>The engine's {@code DlqReplayProcessor} consumes this topic and executes the full validation
 * pipeline (destination safety, schema compatibility, ENGINE signing) before forwarding the
 * corrected record to its target ingress surface.
 *
 * <p>Records are keyed by {@link DlqReplayCommand#getDlqEntryRef()} so that replay results on
 * {@code dlq.replay-results} can be correlated back to the originating command using the same key.
 *
 * <p>Use {@link DlqReplayCommandBuilder} to construct a well-formed command from a {@link
 * io.taktx.dto.DlqEnvelope}.
 *
 * <p>This is a <em>Community</em>-tier feature.
 */
public class DlqReplayCommandProducer {

  private static final Logger log = LoggerFactory.getLogger(DlqReplayCommandProducer.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final KafkaProducer<String, byte[]> producer;

  /**
   * Creates a new {@code DlqReplayCommandProducer}.
   *
   * @param taktPropertiesHelper helper carrying Kafka bootstrap and namespace configuration
   */
  public DlqReplayCommandProducer(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    Properties props = taktPropertiesHelper.getKafkaProducerProperties();
    this.producer = new KafkaProducer<>(props, new StringSerializer(), new ByteArraySerializer());
  }

  /**
   * Serialises {@code command} to protobuf and publishes it to the prefixed {@code dlq.replay}
   * topic.
   *
   * <p>The record key is set to {@link DlqReplayCommand#getDlqEntryRef()} so that operators can
   * correlate results on {@code dlq.replay-results} by the same reference.
   *
   * @param command the replay command to submit; must not be {@code null}
   * @throws IllegalArgumentException if {@code command} is {@code null}
   * @throws IllegalStateException if protobuf serialisation or Kafka send fails
   */
  public void submit(DlqReplayCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("DlqReplayCommand must not be null");
    }
    String topic = taktPropertiesHelper.getPrefixedTopicName(Topics.DLQ_REPLAY.getTopicName());
    byte[] valueBytes = DlqProtoMapper.toProto(command).toByteArray();
    ProducerRecord<String, byte[]> bytesRecord =
        new ProducerRecord<>(topic, command.getDlqEntryRef(), valueBytes);
    producer.send(
        bytesRecord,
        (metadata, ex) -> {
          if (ex != null) {
            log.error(
                "Failed to publish DlqReplayCommand to topic={} dlqEntryRef={}: {}",
                topic,
                command.getDlqEntryRef(),
                ex.getMessage(),
                ex);
          } else {
            log.debug(
                "DlqReplayCommand published: topic={} partition={} offset={} dlqEntryRef={}",
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                command.getDlqEntryRef());
          }
        });
    producer.flush();
  }

  /**
   * Closes the underlying {@link KafkaProducer}. Call this when the client is stopped to release
   * Kafka connections promptly.
   */
  public void close() {
    producer.close();
  }
}
