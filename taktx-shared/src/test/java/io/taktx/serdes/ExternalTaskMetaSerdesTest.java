/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.TopicMetaDTO;
import io.taktx.security.SigningKeyGenerator;
import java.security.KeyPair;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class ExternalTaskMetaSerdesTest {

  @Test
  void serializer_hasCorrectClass() {
    try (ExternalTaskMetaSerializer serializer = new ExternalTaskMetaSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(TopicMetaDTO.class);
    }
  }

  @Test
  void deserializer_hasCorrectClass() {
    try (ExternalTaskMetaDeserializer deserializer = new ExternalTaskMetaDeserializer()) {
      assertThat(deserializer.getClazz()).isEqualTo(TopicMetaDTO.class);
    }
  }

  @Test
  void deserializer_rejectsUnsignedRecordWhenSigningIsRequired() throws Exception {
    TopicMetaDTO topicMeta =
        new TopicMetaDTO("tenant.ns.external-task-trigger-billing", 3, null, (short) 1);

    byte[] bytes;
    try (ExternalTaskMetaSerializer serializer = new ExternalTaskMetaSerializer()) {
      bytes = serializer.serialize("topic-meta-requested", topicMeta);
    }

    KeyPair keyPair = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    try (ExternalTaskMetaDeserializer deserializer = new ExternalTaskMetaDeserializer()) {
      deserializer.configure(
          Map.of(
              JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              publicKeyBase64,
              JsonDeserializer.SIGNING_REQUIRED_CONFIG,
              "true"),
          false);

      assertThatThrownBy(
              () -> deserializer.deserialize("topic-meta-requested", new RecordHeaders(), bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no X-TaktX-Signature header");
    }
  }
}
