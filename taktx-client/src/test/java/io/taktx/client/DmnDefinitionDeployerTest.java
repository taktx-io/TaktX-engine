/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.taktx.dto.DmnDefinitionsKey;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;

class DmnDefinitionDeployerTest {

  @Test
  void deployResource_classpathWildcard_deploysAllMatchingDmn() throws IOException {
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", "default");
    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);

    AtomicInteger calls = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    KafkaProducer<String, XmlDmnDefinitionsDTO> mockProducer = mock(KafkaProducer.class);

    DmnDefinitionDeployer deployer =
        new DmnDefinitionDeployer(helper, mockProducer) {
          @Override
          public ParsedDmnDefinitionsDTO deployInputStream(String xml) {
            calls.incrementAndGet();
            return ParsedDmnDefinitionsDTO.builder()
                .definitionsKey(new DmnDefinitionsKey("test-" + calls.get(), "hash" + calls.get()))
                .name("test")
                .decisions(java.util.List.of())
                .build();
          }
        };

    deployer.deployResource("classpath:dmn/*.dmn");

    assertThat(calls.get()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void deployResource_fileSystemPath_deploysFile() throws IOException {
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", "default");
    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);

    AtomicInteger calls = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    KafkaProducer<String, XmlDmnDefinitionsDTO> mockProducer = mock(KafkaProducer.class);

    DmnDefinitionDeployer deployer =
        new DmnDefinitionDeployer(helper, mockProducer) {
          @Override
          public ParsedDmnDefinitionsDTO deployInputStream(String xml) {
            calls.incrementAndGet();
            return ParsedDmnDefinitionsDTO.builder()
                .definitionsKey(new DmnDefinitionsKey("test-" + calls.get(), "hash" + calls.get()))
                .name("test")
                .decisions(java.util.List.of())
                .build();
          }
        };

    Path resourcesDir = Paths.get("src/test/resources/dmn").toAbsolutePath();
    deployer.deployResource("file:" + resourcesDir + "/*.dmn");

    assertThat(calls.get()).isGreaterThanOrEqualTo(1);
  }
}
