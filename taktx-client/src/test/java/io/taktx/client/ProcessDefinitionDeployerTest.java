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

import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDeployerTest {

  @Test
  void testDeployResourceClasspathAndFile() throws IOException {
    // No need to provide bootstrap.servers because we inject a mock producer
    Properties props = new Properties();
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", "default");
    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);

    AtomicInteger calls = new AtomicInteger(0);

    // Mock KafkaProducer to avoid network activity
    @SuppressWarnings("unchecked")
    KafkaProducer<String, XmlDefinitionsDTO> mockProducer = mock(KafkaProducer.class);

    // Create an anonymous subclass that overrides deployInputStream to count calls and return a
    // simple DTO
    ProcessDefinitionDeployer deployer =
        new ProcessDefinitionDeployer(helper, mockProducer) {
          @Override
          public ParsedDefinitionsDTO deployInputStream(String xml) {
            calls.incrementAndGet();
            // Return a minimal ParsedDefinitionsDTO without invoking JAXB
            return new ParsedDefinitionsDTO(
                new DefinitionsKey("process-" + calls.get(), "hash" + calls.get()),
                null,
                null,
                null,
                null,
                null);
          }
        };

    // Deploy classpath resources using wildcard
    deployer.deployResource("classpath:processes/*.bpmn");

    // There are two test resources we added
    assertThat(calls.get()).isGreaterThanOrEqualTo(1);

    int afterClasspathCalls = calls.get();

    // Deploy filesystem resource using file: prefix to the same directory
    Path resourcesDir = Paths.get("src/test/resources/processes").toAbsolutePath();
    String pattern = "file:" + resourcesDir.toString() + "/*.bpmn";

    deployer.deployResource(pattern);

    // Should have increased calls by at least the two files
    assertThat(calls.get()).isGreaterThan(afterClasspathCalls);
  }
}
