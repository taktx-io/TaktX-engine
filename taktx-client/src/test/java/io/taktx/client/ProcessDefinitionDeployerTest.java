/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
    TaktPropertiesHelper helper = new TaktPropertiesHelper(new Properties());

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
