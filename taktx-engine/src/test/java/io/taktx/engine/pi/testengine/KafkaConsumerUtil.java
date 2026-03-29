/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.testengine;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

public class KafkaConsumerUtil<K, V> {
  private static final Logger LOG = Logger.getLogger(KafkaConsumerUtil.class);

  private final String topic;
  private final Consumer<ConsumerRecord<K, V>> consumer;
  private KafkaConsumer<K, V> kafkaConsumer;
  private volatile boolean running = true;

  public KafkaConsumerUtil(
      String groupId,
      String topic,
      String keyDeserializerClass,
      String valueDeserializerClass,
      Consumer<ConsumerRecord<K, V>> consumer) {
    LOG.info("Creating Kafka consumer for topic " + topic);
    this.topic = topic;
    this.consumer = consumer;
    Properties props = new Properties();
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializerClass);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializerClass);
    props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, topic + "-test-consumer");

    kafkaConsumer = new KafkaConsumer<>(props);

    kafkaConsumer.subscribe(Collections.singletonList(topic));

    start();
  }

  void start() {
    Thread t =
        new Thread(
            () -> {
              LOG.info("Starting Kafka consumer for topic " + topic);
              while (running) {
                kafkaConsumer
                    .poll(Duration.ofMillis(100))
                    .forEach(
                        consumerRecord -> {
                          if (running) {
                            LOG.info("Received record for topic: " + topic);
                            consumer.accept(consumerRecord);
                          }
                        });
              }
              LOG.info("Stopping Kafka consumer for topic " + topic);
              try {
                if (kafkaConsumer != null) {
                  kafkaConsumer.unsubscribe();
                  kafkaConsumer.close(CloseOptions.timeout(Duration.ofMillis(100)));
                }
                LOG.info("Stopped Kafka consumer for topic " + topic);
                kafkaConsumer = null;
              } catch (Throwable t2) {
                t2.printStackTrace();
              }
            });
    t.setDaemon(true);
    t.start();
  }

  public void stop() {
    running = false;
    KafkaConsumer<K, V> c = kafkaConsumer;
    if (c != null) {
      c.wakeup();
    }
  }
}
