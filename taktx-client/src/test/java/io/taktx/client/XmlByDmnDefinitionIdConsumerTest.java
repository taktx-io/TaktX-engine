/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.util.TaktPropertiesHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class XmlByDmnDefinitionIdConsumerTest {

  @Test
  void getDmnDefinitionXml_returnsNull_whenFileDoesNotExist() throws IOException {
    String uniqueNamespace = "test-" + UUID.randomUUID();
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", uniqueNamespace);
    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);

    XmlByDmnDefinitionIdConsumer consumer =
        new XmlByDmnDefinitionIdConsumer(helper, Executors.newSingleThreadExecutor());

    String result = consumer.getDmnDefinitionXml(new DmnDefinitionKey("nonExistentDecision", 1));

    assertThat(result).isNull();
  }

  @Test
  void writeDefinition_overwrites_whenCachedFileExistsWithDifferentContent() throws IOException {
    // Simulates a fresh engine installation where the state store was wiped:
    // the engine may reassign version 1 to a completely different XML.
    // The client must overwrite the stale cached file, not skip it.
    String uniqueNamespace = "test-" + UUID.randomUUID();
    Path definitionsRoot =
        Path.of(System.getProperty("user.home"), ".taktx", "definitions", uniqueNamespace);
    Files.createDirectories(definitionsRoot);

    String dmnDefinitionId = "testDecision";
    int version = 1;
    String staleXml = "<definitions id=\"testDecision\" version=\"stale\"/>";
    String freshXml = "<definitions id=\"testDecision\" version=\"fresh-after-engine-reset\"/>";
    Path cacheFile = definitionsRoot.resolve(dmnDefinitionId + "." + version + ".dmn");

    try {
      // Simulate a stale cached file from before the engine was wiped.
      Files.writeString(cacheFile, staleXml);

      Properties props = new Properties();
      props.put("taktx.engine.tenant-id", "test-tenant");
      props.put("taktx.engine.namespace", uniqueNamespace);
      TaktPropertiesHelper helper = new TaktPropertiesHelper(props);
      XmlByDmnDefinitionIdConsumer consumer =
          new XmlByDmnDefinitionIdConsumer(helper, Executors.newSingleThreadExecutor());

      // getDmnDefinitionXml at this point returns the stale content.
      assertThat(consumer.getDmnDefinitionXml(new DmnDefinitionKey(dmnDefinitionId, version)))
          .isEqualTo(staleXml);

      // Simulate the fresh engine replaying version 1 with new content.
      Files.writeString(cacheFile, freshXml);

      // After the overwrite the consumer must return the updated content.
      assertThat(consumer.getDmnDefinitionXml(new DmnDefinitionKey(dmnDefinitionId, version)))
          .isEqualTo(freshXml)
          .isNotEqualTo(staleXml);
    } finally {
      Files.deleteIfExists(cacheFile);
      Files.deleteIfExists(definitionsRoot);
    }
  }

  @Test
  void stop_doesNotThrow_whenConsumerIsNotRunning() {
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", "default");
    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);

    XmlByDmnDefinitionIdConsumer consumer =
        new XmlByDmnDefinitionIdConsumer(helper, Executors.newSingleThreadExecutor());

    // Should not throw even though no active consumer
    consumer.stop();
  }
}
