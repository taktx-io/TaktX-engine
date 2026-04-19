/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.taktx.dto.Constants;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.SigningKeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScheduleCommandDeserializerTest {

  private static final String TOPIC = "acme.prod.schedule-commands";

  private final ScheduleCommandDeserializer deserializer = new ScheduleCommandDeserializer();

  @AfterEach
  void tearDown() {
    EngineSigningKeysHolder.clear();
  }

  @Test
  void unsignedScheduleCommand_isRejected() {
    byte[] payload = serialize(oneTimeSchedule());

    assertThatThrownBy(() -> deserializer.deserialize(TOPIC, new RecordHeaders(), payload))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no X-TaktX-Signature header");
  }

  @Test
  void signedScheduleCommand_isDeserialized() {
    OneTimeScheduleDTO schedule = oneTimeSchedule();
    byte[] payload = serialize(schedule);
    String keyId = "engine-key-1";
    RecordHeaders headers = signedHeaders(payload, keyId);

    MessageScheduleDTO result = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(result).isInstanceOf(OneTimeScheduleDTO.class);
    assertThat((OneTimeScheduleDTO) result)
        .extracting(OneTimeScheduleDTO::getWhen, OneTimeScheduleDTO::getInstantiationTime)
        .containsExactly(schedule.getWhen(), schedule.getInstantiationTime());
    assertThat(result.getMessage()).isInstanceOf(StartCommandDTO.class);
  }

  @Test
  void signedTombstone_isAcceptedAndReturnsNull() {
    String keyId = "engine-key-1";
    RecordHeaders headers = signedHeaders(new byte[0], keyId);

    MessageScheduleDTO result = deserializer.deserialize(TOPIC, headers, (byte[]) null);

    assertThat(result).isNull();
  }

  private RecordHeaders signedHeaders(byte[] payload, String keyId) {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    EngineSigningKeysHolder.set(
        resolvedKeyId -> keyId.equals(resolvedKeyId) ? publicKeyBase64 : null);

    byte[] signature = Ed25519Service.sign(payload, privateKeyBase64);
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE,
        (keyId + "." + Base64.getEncoder().encodeToString(signature))
            .getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private byte[] serialize(MessageScheduleDTO schedule) {
    try (ObjectMapperSerde<MessageScheduleDTO> serde =
        new ObjectMapperSerde<>(MessageScheduleDTO.class)) {
      return serde.serializer().serialize(TOPIC, schedule);
    }
  }

  private OneTimeScheduleDTO oneTimeSchedule() {
    return new OneTimeScheduleDTO(
        new StartCommandDTO(
            UUID.randomUUID(),
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty()),
        1_000_000L,
        1_060_000L);
  }
}
