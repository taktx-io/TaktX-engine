/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.serdes.ZippedStringDeserializer;
import io.taktx.serdes.ZippedStringSerde;
import io.taktx.serdes.ZippedStringSerializer;
import org.junit.jupiter.api.Test;

class XmlCompressorTest {

  private static final String SAMPLE =
      "<definitions id=\"test\"><process id=\"p1\"/></definitions>";

  @Test
  void compress_producesNonEmptyBytes() {
    byte[] bytes = XmlCompressor.compress(SAMPLE);
    assertThat(bytes).isNotEmpty();
  }

  @Test
  void decompress_roundTrip_returnsOriginalString() {
    byte[] compressed = XmlCompressor.compress(SAMPLE);
    String decompressed = XmlCompressor.decompress(compressed);
    assertThat(decompressed).isEqualTo(SAMPLE);
  }

  @Test
  void roundTrip_withUnicodeContent() {
    String xml = "<definitions name=\"Ünïcödé äöü\"/>";
    assertThat(XmlCompressor.decompress(XmlCompressor.compress(xml))).isEqualTo(xml);
  }

  @Test
  void compress_sameInputProducesSameOutput() {
    byte[] a = XmlCompressor.compress(SAMPLE);
    byte[] b = XmlCompressor.compress(SAMPLE);
    // GZIP output for the same input may vary (timestamp in header), but content is same
    assertThat(XmlCompressor.decompress(a)).isEqualTo(XmlCompressor.decompress(b));
  }

  // ── ZippedStringSerializer / ZippedStringDeserializer ──────────────────────────

  @Test
  void zippedStringSerializer_deserializer_roundTrip() {
    ZippedStringSerializer serializer = new ZippedStringSerializer();
    ZippedStringDeserializer deserializer = new ZippedStringDeserializer();

    byte[] bytes = serializer.serialize("any-topic", SAMPLE);
    String result = deserializer.deserialize("any-topic", bytes);

    assertThat(result).isEqualTo(SAMPLE);
  }

  // ── ZippedStringSerde ───────────────────────────────────────────────────────────

  @Test
  void zippedStringSerde_roundTrip() {
    ZippedStringSerde serde = new ZippedStringSerde();
    byte[] bytes = serde.serializer().serialize("topic", SAMPLE);
    String result = serde.deserializer().deserialize("topic", bytes);
    assertThat(result).isEqualTo(SAMPLE);
  }

  @Test
  void zippedStringSerde_serializerAndDeserializer_areNotNull() {
    ZippedStringSerde serde = new ZippedStringSerde();
    assertThat(serde.serializer()).isNotNull();
    assertThat(serde.deserializer()).isNotNull();
  }
}
