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
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPersistentSigningIdentitySourceTest {

  @TempDir Path tempDir;

  @Test
  void currentIdentity_generatesAndReusesPersistedIdentityAcrossRestart() {
    Path directory = tempDir.resolve("managed-identity");

    SigningIdentity firstIdentity =
        new LocalPersistentSigningIdentitySource(
                Map.of(), new Properties(), directory.toString(), "client-")
            .currentIdentity();

    SigningIdentity reloadedIdentity =
        new LocalPersistentSigningIdentitySource(
                Map.of(), new Properties(), directory.toString(), "different-")
            .currentIdentity();

    assertThat(firstIdentity).isEqualTo(reloadedIdentity);
    assertThat(firstIdentity.getKeyId()).startsWith("client-");
    assertThat(firstIdentity.getPrivateKeyBase64()).isNotBlank();
    assertThat(firstIdentity.getPublicKeyBase64()).isNotBlank();
    assertThat(firstIdentity.getAlgorithm()).isEqualTo("Ed25519");
    assertThat(Files.exists(directory.resolve(LocalPersistentSigningIdentitySource.IDENTITY_FILENAME)))
        .isTrue();
  }

  @Test
  void currentIdentity_honorsConfiguredDirectoryAndKeyPrefixProperties() {
    Path directory = tempDir.resolve("configured-home");
    Properties properties = new Properties();
    properties.setProperty(LocalPersistentSigningIdentitySource.DIRECTORY_SYS_PROP, directory.toString());
    properties.setProperty(LocalPersistentSigningIdentitySource.KEY_ID_PREFIX_SYS_PROP, "worker-");

    SigningIdentity identity =
        new LocalPersistentSigningIdentitySource(Map.of(), properties, null, null).currentIdentity();

    assertThat(identity.getKeyId()).startsWith("worker-");
    assertThat(Files.exists(directory.resolve(LocalPersistentSigningIdentitySource.IDENTITY_FILENAME)))
        .isTrue();
  }

  @Test
  void currentIdentity_rejectsCorruptPersistedIdentityWithoutGeneratingReplacement() throws Exception {
    Path directory = Files.createDirectories(tempDir.resolve("corrupt"));
    Path identityFile = directory.resolve(LocalPersistentSigningIdentitySource.IDENTITY_FILENAME);
    String corruptIdentity = "keyId=worker-a\npublicKeyBase64=public-a\nalgorithm=Ed25519\n";
    Files.writeString(
        identityFile,
        corruptIdentity);

    LocalPersistentSigningIdentitySource source =
        new LocalPersistentSigningIdentitySource(Map.of(), new Properties(), directory.toString(), "worker-");

    assertThatThrownBy(source::currentIdentity)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing required property 'privateKeyBase64'");
    assertThat(Files.readString(identityFile)).isEqualTo(corruptIdentity);
  }
}


