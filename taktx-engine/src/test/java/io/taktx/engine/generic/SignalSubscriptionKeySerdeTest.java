/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.Constants;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.pd.SignalProcessor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalSubscriptionKeySerdeTest {

  @Test
  void signalInstanceSubscriptionKeySerializer_rangeCoversOnlyMatchingHashBucket() {
    SignalInstanceSubscriptionKeySerializer serializer =
        new SignalInstanceSubscriptionKeySerializer();
    byte[] hash = repeatedByte(0x11);
    byte[] otherHash = repeatedByte(0x12);

    byte[] start =
        serializer.serialize(
            "topic", new SignalInstanceSubscriptionKeyDTO(hash, Constants.MIN_UUID, List.of()));
    byte[] end =
        serializer.serialize(
            "topic",
            new SignalInstanceSubscriptionKeyDTO(
                hash, Constants.MAX_UUID, List.of(Constants.MAX_LONG)));

    byte[] inRangeMin =
        serializer.serialize(
            "topic", new SignalInstanceSubscriptionKeyDTO(hash, Constants.MIN_UUID, List.of(1L)));
    byte[] inRangeMax =
        serializer.serialize(
            "topic",
            new SignalInstanceSubscriptionKeyDTO(
                hash, new java.util.UUID(0L, Long.MAX_VALUE), List.of()));
    byte[] differentHash =
        serializer.serialize(
            "topic",
            new SignalInstanceSubscriptionKeyDTO(otherHash, Constants.MIN_UUID, List.of(1L)));

    assertThat(isWithinInclusiveRange(inRangeMin, start, end)).isTrue();
    assertThat(isWithinInclusiveRange(inRangeMax, start, end)).isTrue();
    assertThat(isWithinInclusiveRange(differentHash, start, end)).isFalse();
  }

  @Test
  void signalDefinitionSubscriptionKeySerializer_supportsPrefixUpperBoundRangeScan() {
    byte[] hash = repeatedByte(0x22);
    byte[] upperBound = SignalProcessor.prefixExclusiveUpperBound(hash);
    SignalDefinitionSubscriptionKeySerializer serializer =
        new SignalDefinitionSubscriptionKeySerializer();

    byte[] actual =
        serializer.serialize(
            "topic",
            new SignalDefinitionSubscriptionKeyDTO(
                hash, new ProcessDefinitionKey("proc", 7), "SignalStartEvent"));
    byte[] end =
        serializer.serialize(
            "topic", new SignalDefinitionSubscriptionKeyDTO(upperBound, null, null));

    assertThat(Arrays.compareUnsigned(actual, end)).isLessThan(0);
  }

  @Test
  void signalDefinitionSubscriptionKeySerde_roundTripsStoredKeys() {
    SignalDefinitionSubscriptionKeySerde serde = new SignalDefinitionSubscriptionKeySerde();
    SignalDefinitionSubscriptionKeyDTO original =
        new SignalDefinitionSubscriptionKeyDTO(
            repeatedByte(0x33), new ProcessDefinitionKey("signal-start", 4), "StartEvent_1");

    byte[] bytes = serde.serializer().serialize("topic", original);
    SignalDefinitionSubscriptionKeyDTO decoded = serde.deserializer().deserialize("topic", bytes);

    assertThat(decoded).isEqualTo(original);
  }

  private static boolean isWithinInclusiveRange(byte[] candidate, byte[] start, byte[] end) {
    return Arrays.compareUnsigned(candidate, start) >= 0
        && Arrays.compareUnsigned(candidate, end) <= 0;
  }

  private static byte[] repeatedByte(int value) {
    byte[] bytes = new byte[SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES];
    Arrays.fill(bytes, (byte) value);
    return bytes;
  }
}
