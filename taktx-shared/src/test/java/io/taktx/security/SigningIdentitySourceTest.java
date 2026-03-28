/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningIdentitySourceTest {

  @TempDir Path tempDir;

  @Test
  void generatedSource_returnsStableIdentityWithRequestedPrefix() {
    GeneratedSigningIdentitySource source = new GeneratedSigningIdentitySource("engine-");

    SigningIdentity first = source.currentIdentity();
    SigningIdentity second = source.currentIdentity();

    assertThat(first).isEqualTo(second);
    assertThat(first.getKeyId()).startsWith("engine-");
    assertThat(first.getPrivateKeyBase64()).isNotBlank();
    assertThat(first.getPublicKeyBase64()).isNotBlank();
    assertThat(first.getAlgorithm()).isEqualTo("Ed25519");
  }

  @Test
  void staticSource_canSwapIdentity() {
    StaticSigningIdentitySource source =
        new StaticSigningIdentitySource(SigningIdentity.ed25519("key-a", "priv-a", "pub-a"));

    assertThat(source.currentIdentity().getKeyId()).isEqualTo("key-a");

    source.setIdentity(SigningIdentity.ed25519("key-b", "priv-b", "pub-b"));

    assertThat(source.currentIdentity().getKeyId()).isEqualTo("key-b");
  }

  @Test
  void environmentSource_readsPropertiesAndAppliesKeyIdOverride() {
    Map<String, String> env = new HashMap<>();
    Properties props = new Properties();
    props.setProperty("taktx.signing.private-key", "private-123");
    props.setProperty("taktx.signing.public-key", "public-123");
    props.setProperty("taktx.signing.key-id", "original-key");

    EnvironmentWorkerSigningIdentitySource source =
        new EnvironmentWorkerSigningIdentitySource(env, props, "override-key");

    SigningIdentity identity = source.currentIdentity();

    assertThat(identity.getKeyId()).isEqualTo("override-key");
    assertThat(identity.getPrivateKeyBase64()).isEqualTo("private-123");
    assertThat(identity.getPublicKeyBase64()).isEqualTo("public-123");
  }

  @Test
  void environmentSource_returnsNullWhenNoPrivateKeyConfigured() {
    EnvironmentWorkerSigningIdentitySource source =
        new EnvironmentWorkerSigningIdentitySource(Map.of(), new Properties(), null);

    assertThat(source.currentIdentity()).isNull();
  }

  @Test
  void environmentSource_throwsWhenPrivateKeyConfiguredWithoutKeyId() {
    Map<String, String> env = new HashMap<>();
    Properties props = new Properties();
    props.setProperty("taktx.signing.private-key", "private-123");

    EnvironmentWorkerSigningIdentitySource source =
        new EnvironmentWorkerSigningIdentitySource(env, props, null);

    assertThatThrownBy(source::currentIdentity)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TAKTX_SIGNING_PRIVATE_KEY");
  }

  @Test
  void fileSource_readsIdentityFromConfiguredFiles() throws Exception {
    Path keyId = Files.writeString(tempDir.resolve("key-id"), "worker-file-key\n");
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "private-123\n");
    Path publicKey = Files.writeString(tempDir.resolve("public-key.b64"), "public-123\n");

    AtomicLong now = new AtomicLong(0L);
    FileSigningIdentitySource source =
        new FileSigningIdentitySource(
            Map.of(),
            new Properties(),
            keyId.toString(),
            privateKey.toString(),
            publicKey.toString(),
            1000L,
            now::get);

    SigningIdentity identity = source.currentIdentity();

    assertThat(identity.getKeyId()).isEqualTo("worker-file-key");
    assertThat(identity.getPrivateKeyBase64()).isEqualTo("private-123");
    assertThat(identity.getPublicKeyBase64()).isEqualTo("public-123");
  }

  @Test
  void fileSource_returnsUpdatedIdentityAfterFilesChange() throws Exception {
    Path keyId = Files.writeString(tempDir.resolve("key-id"), "worker-file-key-a");
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "private-a");
    Path publicKey = Files.writeString(tempDir.resolve("public-key.b64"), "public-a");

    AtomicLong now = new AtomicLong(0L);
    FileSigningIdentitySource source =
        new FileSigningIdentitySource(
            Map.of(),
            new Properties(),
            keyId.toString(),
            privateKey.toString(),
            publicKey.toString(),
            1000L,
            now::get);

    assertThat(source.currentIdentity().getKeyId()).isEqualTo("worker-file-key-a");

    Files.writeString(keyId, "worker-file-key-b\n");
    Files.writeString(privateKey, "private-b\n");
    Files.writeString(publicKey, "public-b\n");

    assertThat(source.currentIdentity().getKeyId()).isEqualTo("worker-file-key-a");

    now.set(1000L);
    SigningIdentity rotated = source.currentIdentity();

    assertThat(rotated.getKeyId()).isEqualTo("worker-file-key-b");
    assertThat(rotated.getPrivateKeyBase64()).isEqualTo("private-b");
    assertThat(rotated.getPublicKeyBase64()).isEqualTo("public-b");
  }

  @Test
  void fileSource_keepsLastKnownGoodIdentityWhenRefreshFails() throws Exception {
    Path keyId = Files.writeString(tempDir.resolve("key-id"), "worker-file-key-a");
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "private-a");
    Path publicKey = Files.writeString(tempDir.resolve("public-key.b64"), "public-a");

    AtomicLong now = new AtomicLong(0L);
    FileSigningIdentitySource source =
        new FileSigningIdentitySource(
            Map.of(),
            new Properties(),
            keyId.toString(),
            privateKey.toString(),
            publicKey.toString(),
            1000L,
            now::get);

    SigningIdentity initial = source.currentIdentity();
    Files.writeString(privateKey, "   \n");

    now.set(1000L);
    SigningIdentity retained = source.currentIdentity();

    assertThat(retained).isEqualTo(initial);
    assertThat(retained.getKeyId()).isEqualTo("worker-file-key-a");
  }

  @Test
  void fileSource_throwsWhenPrivateKeyFileConfiguredWithoutKeyIdFile() throws Exception {
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "private-123");

    FileSigningIdentitySource source =
        new FileSigningIdentitySource(
            Map.of(), new Properties(), null, privateKey.toString(), null, 1000L, () -> 0L);

    assertThatThrownBy(source::currentIdentity)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TAKTX_SIGNING_FILE_KEY_ID_PATH");
  }

  @Test
  void fileSource_throwsOnInitialConfiguredLoadFailure() throws Exception {
    Path keyId = Files.writeString(tempDir.resolve("key-id"), "worker-file-key-a");
    Path privateKey = Files.writeString(tempDir.resolve("private-key.b64"), "   \n");

    FileSigningIdentitySource source =
        new FileSigningIdentitySource(
            Map.of(),
            new Properties(),
            keyId.toString(),
            privateKey.toString(),
            null,
            1000L,
            () -> 0L);

    assertThatThrownBy(source::currentIdentity)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("private key")
        .hasMessageContaining("blank");
  }
}
