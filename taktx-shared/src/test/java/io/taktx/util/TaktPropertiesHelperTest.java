/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

class TaktPropertiesHelperTest {

  private static Properties validProps() {
    Properties p = new Properties();
    p.put("taktx.engine.tenant-id", "acme");
    p.put("taktx.engine.namespace", "prod");
    return p;
  }

  @Test
  void constructor_withValidProperties_succeeds() {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(validProps());
    assertThat(helper.getTenantId()).isEqualTo("acme");
    assertThat(helper.getNamespace()).isEqualTo("prod");
  }

  @Test
  void constructor_missingTenantId_throwsIllegalArgument() {
    Properties p = new Properties();
    p.put("taktx.engine.namespace", "prod");
    assertThatThrownBy(() -> new TaktPropertiesHelper(p))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.engine.tenant-id");
  }

  @Test
  void constructor_blankTenantId_throwsIllegalArgument() {
    Properties p = new Properties();
    p.put("taktx.engine.tenant-id", "   ");
    p.put("taktx.engine.namespace", "prod");
    assertThatThrownBy(() -> new TaktPropertiesHelper(p))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_missingNamespace_throwsIllegalArgument() {
    Properties p = new Properties();
    p.put("taktx.engine.tenant-id", "acme");
    assertThatThrownBy(() -> new TaktPropertiesHelper(p))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.engine.namespace");
  }

  @Test
  void getPrefixedTopicName_returnsCorrectFormat() {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(validProps());
    assertThat(helper.getPrefixedTopicName("definitions")).isEqualTo("acme.prod.definitions");
  }

  @Test
  void getBootstrapServers_returnsNull_whenNotSet() {
    assertThat(new TaktPropertiesHelper(validProps()).getBootstrapServers()).isNull();
  }

  @Test
  void getBootstrapServers_returnsValue_whenSet() {
    Properties p = validProps();
    p.put("bootstrap.servers", "localhost:9092");
    assertThat(new TaktPropertiesHelper(p).getBootstrapServers()).isEqualTo("localhost:9092");
  }

  @Test
  void getKafkaConsumerProperties_setsRequiredKeys() {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(validProps());
    Properties props =
        helper.getKafkaConsumerProperties(
            "my-group",
            org.apache.kafka.common.serialization.StringDeserializer.class,
            org.apache.kafka.common.serialization.StringDeserializer.class,
            "earliest");

    assertThat(props.getProperty(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("my-group");
    assertThat(props.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    assertThat(props.getProperty(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG))
        .isEqualTo("false");
  }

  @Test
  void getKafkaProducerProperties_setsRequiredKeys() {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(validProps());
    Properties props = helper.getKafkaProducerProperties();

    assertThat(props.getProperty(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(props.getProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo("true");
  }

  @Test
  void getExternalTaskAckStrategy_returnsDefault() {
    assertThat(new TaktPropertiesHelper(validProps()).getExternalTaskAckStrategy())
        .isEqualTo("EXPLICIT_BATCH");
  }

  @Test
  void getEffectiveThreadingStrategy_returnsDefault() {
    assertThat(new TaktPropertiesHelper(validProps()).getEffectiveThreadingStrategy())
        .isEqualTo("VIRTUAL_THREAD_WAIT");
  }

  @Test
  void getExternalTaskConsumerThreads_returnsDefault() {
    assertThat(new TaktPropertiesHelper(validProps()).getExternalTaskConsumerThreads())
        .isEqualTo(1);
  }

  @Test
  void getExternalTaskConsumerMaxPollRecords_returnsDefault() {
    assertThat(new TaktPropertiesHelper(validProps()).getExternalTaskConsumerMaxPollRecords())
        .isEqualTo(500);
  }

  @Test
  void getExternalTaskConsumerPollTimeoutMs_returnsDefault() {
    assertThat(new TaktPropertiesHelper(validProps()).getExternalTaskConsumerPollTimeoutMs())
        .isEqualTo(100);
  }
}
