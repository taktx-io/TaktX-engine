/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.security.EnvironmentWorkerSigningIdentitySource;
import io.taktx.security.FileSigningIdentitySource;
import io.taktx.security.GeneratedSigningIdentitySource;
import io.taktx.security.SigningIdentitySource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaktXClientBuilderSigningIdentitySourceTest {

  @TempDir Path tempDir;

  @Test
  void resolveSigningIdentitySource_defaultsToGeneratedSourceWhenNoIdentityIsConfigured() {
    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SigningIdentitySource source = builder.resolveSigningIdentitySource(new Properties());

    assertThat(source).isInstanceOf(GeneratedSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).startsWith("client-");
  }

  @Test
  void resolveSigningIdentitySource_prefersExplicitSigningIdentitySource() {
    SigningIdentitySource explicitSource = () -> null;

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();
    builder.withSigningIdentitySource(explicitSource);

    SigningIdentitySource source = builder.resolveSigningIdentitySource(new Properties());

    assertThat(source).isSameAs(explicitSource);
  }

  @Test
  void resolveSigningIdentitySource_defaultsToEnvironmentSourceWhenIdentityIsConfigured() {
    Properties props = new Properties();
    props.setProperty("taktx.signing.private-key", "private-123");
    props.setProperty("taktx.signing.public-key", "public-123");
    props.setProperty("taktx.signing.key-id", "env-worker-key");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SigningIdentitySource source = builder.resolveSigningIdentitySource(props);

    assertThat(source).isInstanceOf(EnvironmentWorkerSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).isEqualTo("env-worker-key");
  }

  @Test
  void resolveSigningIdentitySource_defaultSourceStillFailsFastOnMalformedEnvironmentConfig() {
    Properties props = new Properties();
    props.setProperty("taktx.signing.private-key", "private-123");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    assertThatThrownBy(() -> builder.resolveSigningIdentitySource(props))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TAKTX_SIGNING_PRIVATE_KEY");
  }

  @Test
  void resolveSigningIdentitySource_supportsFileSource() throws Exception {
    Properties props = new Properties();
    props.setProperty("taktx.signing.identity-source", "file");
    props.setProperty(
        "taktx.signing.file.key-id-path",
        Files.writeString(tempDir.resolve("key-id"), "worker-key").toString());
    props.setProperty(
        "taktx.signing.file.private-key-path",
        Files.writeString(tempDir.resolve("private-key.b64"), "private-value").toString());
    props.setProperty(
        "taktx.signing.file.public-key-path",
        Files.writeString(tempDir.resolve("public-key.b64"), "public-value").toString());

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SigningIdentitySource source = builder.resolveSigningIdentitySource(props);

    assertThat(source).isInstanceOf(FileSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).isEqualTo("worker-key");
  }

  @Test
  void resolveSigningIdentitySource_supportsGeneratedSource() {
    Properties props = new Properties();
    props.setProperty("taktx.signing.identity-source", "generated");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SigningIdentitySource source = builder.resolveSigningIdentitySource(props);

    assertThat(source).isInstanceOf(GeneratedSigningIdentitySource.class);
    assertThat(source.currentIdentity().getKeyId()).startsWith("client-");
  }

  @Test
  void resolveSigningIdentitySource_explicitEnvironmentSourceDoesNotFallBackToGenerated() {
    Properties props = new Properties();
    props.setProperty("taktx.signing.identity-source", "env");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    SigningIdentitySource source = builder.resolveSigningIdentitySource(props);

    assertThat(source).isInstanceOf(EnvironmentWorkerSigningIdentitySource.class);
    assertThat(source.currentIdentity()).isNull();
  }

  @Test
  void resolveSigningIdentitySource_rejectsUnsupportedSourceValues() {
    Properties props = new Properties();
    props.setProperty("taktx.signing.identity-source", "vault");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    assertThatThrownBy(() -> builder.resolveSigningIdentitySource(props))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.signing.identity-source")
        .hasMessageContaining("env, file, generated");
  }
}
