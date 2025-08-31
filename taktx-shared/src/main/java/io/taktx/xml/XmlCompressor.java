/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmlCompressor {
  private XmlCompressor() {
    // prevent instantiation
  }

  public static byte[] compress(String xml) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
      gzip.write(xml.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      throw new IllegalStateException(e);
    }
    return bos.toByteArray();
  }

  public static String decompress(byte[] compressed) {
    ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
    try (GZIPInputStream gzip = new GZIPInputStream(bis);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = gzip.read(buffer)) > 0) {
        out.write(buffer, 0, len);
      }
      return out.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      throw new IllegalStateException(e);
    }
  }
}
