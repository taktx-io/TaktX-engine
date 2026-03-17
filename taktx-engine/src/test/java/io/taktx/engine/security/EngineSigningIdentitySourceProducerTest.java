/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.EnvironmentWorkerSigningIdentitySource;
import io.taktx.security.FileSigningIdentitySource;
import io.taktx.security.SigningIdentitySource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineSigningIdentitySourceProducerTest {

  @TempDir Path tempDir;

  @Test
  void signingIdentitySource_defaultsToGeneratedForEngine() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getSigningIdentitySourceType()).thenReturn("generated");

    SigningIdentitySource source =
        new EngineSigningIdentitySourceProducer(config).signingIdentitySource();

    assertThat(source).isInstanceOf(GeneratedEngineSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).startsWith("engine-");
  }

  @Test
  void signingIdentitySource_canUseEnvironmentBackedSource() {
    EngineSigningIdentitySourceProducer producer =
        new EngineSigningIdentitySourceProducer(mock(TaktConfiguration.class));

    assertThat(producer.create("env")).isInstanceOf(EnvironmentWorkerSigningIdentitySource.class);
    assertThat(producer.create("environment"))
        .isInstanceOf(EnvironmentWorkerSigningIdentitySource.class);
  }

  @Test
  void signingIdentitySource_canUseFileBackedSource() throws Exception {
    Path keyId = Files.writeString(tempDir.resolve("key-id"), "engine-key");
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "private-key");
    Path publicKey = Files.writeString(tempDir.resolve("public-key.b64"), "public-key");

    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.getSigningFileKeyIdPath()).thenReturn(keyId.toString());
    when(config.getSigningFilePrivateKeyPath()).thenReturn(privateKey.toString());
    when(config.getSigningFilePublicKeyPath()).thenReturn(publicKey.toString());

    SigningIdentitySource source = new EngineSigningIdentitySourceProducer(config).create("file");

    assertThat(source).isInstanceOf(FileSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).isEqualTo("engine-key");
  }

  @Test
  void signingIdentitySource_rejectsUnsupportedValues() {
    EngineSigningIdentitySourceProducer producer =
        new EngineSigningIdentitySourceProducer(mock(TaktConfiguration.class));

    assertThatThrownBy(() -> producer.create("vault"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.signing.identity-source")
        .hasMessageContaining("generated, env, file");
  }
}
