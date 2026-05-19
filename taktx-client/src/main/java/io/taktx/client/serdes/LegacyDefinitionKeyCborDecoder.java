/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.dto.ProcessDefinitionKey;
import java.nio.charset.StandardCharsets;

/** Decodes legacy CBOR arrays for simple definition-key DTOs without relying on Jackson. */
final class LegacyDefinitionKeyCborDecoder {

  private LegacyDefinitionKeyCborDecoder() {}

  static ProcessDefinitionKey decodeProcessDefinitionKey(byte[] data) {
    Decoder decoder = new Decoder(data);
    decoder.requireArrayLength(2);
    ProcessDefinitionKey key = new ProcessDefinitionKey(decoder.readText(), decoder.readInteger());
    decoder.requireFullyConsumed();
    return key;
  }

  static DmnDefinitionKey decodeDmnDefinitionKey(byte[] data) {
    Decoder decoder = new Decoder(data);
    decoder.requireArrayLength(2);
    DmnDefinitionKey key = new DmnDefinitionKey(decoder.readText(), decoder.readInteger());
    decoder.requireFullyConsumed();
    return key;
  }

  private static final class Decoder {
    private final byte[] data;
    private int offset;

    private Decoder(byte[] data) {
      if (data == null) {
        throw new IllegalArgumentException("CBOR payload must not be null");
      }
      this.data = data;
    }

    private void requireArrayLength(int expectedLength) {
      int header = readByte();
      int majorType = header >>> 5;
      if (majorType != 4) {
        throw new IllegalArgumentException("Expected CBOR array but found major type " + majorType);
      }
      long actualLength = readLength(header & 0x1F);
      if (actualLength != expectedLength) {
        throw new IllegalArgumentException(
            "Expected CBOR array length " + expectedLength + " but was " + actualLength);
      }
    }

    private String readText() {
      int header = readByte();
      int majorType = header >>> 5;
      if (majorType != 3) {
        throw new IllegalArgumentException(
            "Expected CBOR text string but found major type " + majorType);
      }
      int length = Math.toIntExact(readLength(header & 0x1F));
      requireAvailable(length);
      String value = new String(data, offset, length, StandardCharsets.UTF_8);
      offset += length;
      return value;
    }

    private Integer readInteger() {
      int header = readByte();
      if (header == 0xF6) {
        return null;
      }
      int majorType = header >>> 5;
      long rawValue = readLength(header & 0x1F);
      long signedValue;
      if (majorType == 0) {
        signedValue = rawValue;
      } else if (majorType == 1) {
        signedValue = -1L - rawValue;
      } else {
        throw new IllegalArgumentException(
            "Expected CBOR integer but found major type " + majorType);
      }
      if (signedValue < Integer.MIN_VALUE || signedValue > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Integer value out of range: " + signedValue);
      }
      return (int) signedValue;
    }

    private long readLength(int additionalInfo) {
      return switch (additionalInfo) {
        case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23 ->
            additionalInfo;
        case 24 -> readUnsigned(1);
        case 25 -> readUnsigned(2);
        case 26 -> readUnsigned(4);
        case 27 -> readUnsigned(8);
        case 31 ->
            throw new IllegalArgumentException("Indefinite-length CBOR values are unsupported");
        default ->
            throw new IllegalArgumentException(
                "Unsupported CBOR additional-info value: " + additionalInfo);
      };
    }

    private long readUnsigned(int byteCount) {
      requireAvailable(byteCount);
      long value = 0;
      for (int index = 0; index < byteCount; index++) {
        value = (value << 8) | Byte.toUnsignedInt(data[offset++]);
      }
      return value;
    }

    private int readByte() {
      requireAvailable(1);
      return Byte.toUnsignedInt(data[offset++]);
    }

    private void requireAvailable(int count) {
      if (offset + count > data.length) {
        throw new IllegalArgumentException("Unexpected end of CBOR payload");
      }
    }

    private void requireFullyConsumed() {
      if (offset != data.length) {
        throw new IllegalArgumentException("Unexpected trailing bytes in CBOR payload");
      }
    }
  }
}
