/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(false);

    EngineSigningIdentitySourceProducer producer = new EngineSigningIdentitySourceProducer(config);

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
    when(config.isSecurityProductionMode()).thenReturn(false);
    when(config.getSigningFileKeyIdPath()).thenReturn(keyId.toString());
    when(config.getSigningFilePrivateKeyPath()).thenReturn(privateKey.toString());
    when(config.getSigningFilePublicKeyPath()).thenReturn(publicKey.toString());

    SigningIdentitySource source = new EngineSigningIdentitySourceProducer(config).create("file");

    assertThat(source).isInstanceOf(FileSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).isEqualTo("engine-key");
  }

  @Test
  void signingIdentitySource_rejectsUnsupportedValues() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(false);
    EngineSigningIdentitySourceProducer producer = new EngineSigningIdentitySourceProducer(config);

    assertThatThrownBy(() -> producer.create("vault"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.signing.identity-source")
        .hasMessageContaining("generated, env, file");
  }

  @Test
  void productionMode_rejectsGeneratedSigningSource() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(true);

    EngineSigningIdentitySourceProducer producer = new EngineSigningIdentitySourceProducer(config);

    assertThatThrownBy(() -> producer.create("generated"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("production-mode=true")
        .hasMessageContaining("identity-source=file or env");
  }

  @Test
  void productionMode_rejectsMissingRegistrationSignature() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(true);
    when(config.getEngineKeyRegistrationSignature()).thenReturn(null);

    EngineSigningIdentitySourceProducer producer = new EngineSigningIdentitySourceProducer(config);

    assertThatThrownBy(() -> producer.create("env"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("production-mode=true")
        .hasMessageContaining("TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE");
  }

  @Test
  void productionMode_allowsStableRegisteredSigningSource() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    when(config.isSecurityProductionMode()).thenReturn(true);
    when(config.getEngineKeyRegistrationSignature()).thenReturn("registration-signature");

    EngineSigningIdentitySourceProducer producer = new EngineSigningIdentitySourceProducer(config);

    assertThat(producer.create("env")).isInstanceOf(EnvironmentWorkerSigningIdentitySource.class);
  }
}
