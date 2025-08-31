/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.serdes;

import io.taktx.xml.XmlCompressor;
import org.apache.kafka.common.serialization.Deserializer;

public class ZippedStringDeserializer implements Deserializer<String> {

  @Override
  public String deserialize(String topic, byte[] data) {
    return XmlCompressor.decompress(data);
  }
}
