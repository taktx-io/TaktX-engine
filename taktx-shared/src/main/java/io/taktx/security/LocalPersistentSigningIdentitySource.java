/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Managed local signing-identity source that persists a stable Ed25519 identity on first use.
 *
 * <p>The managed identity is stored as a single {@code identity.properties} file inside a local
 * directory so restarts reuse the same key ID and key material. This source is intended for
 * application-managed local persistence; {@link FileSigningIdentitySource} remains the externally
 * managed mounted-file source.
 */
public class LocalPersistentSigningIdentitySource implements SigningIdentitySource {

  public static final String DIRECTORY_ENV_VAR = "TAKTX_SIGNING_LOCAL_DIRECTORY";
  public static final String DIRECTORY_SYS_PROP = "taktx.signing.local.directory";
  public static final String KEY_ID_PREFIX_ENV_VAR = "TAKTX_SIGNING_LOCAL_KEY_ID_PREFIX";
  public static final String KEY_ID_PREFIX_SYS_PROP = "taktx.signing.local.key-id-prefix";
  public static final String DEFAULT_KEY_ID_PREFIX = "local-";
  public static final String IDENTITY_FILENAME = "identity.properties";

  private static final String KEY_ID_PROPERTY = "keyId";
  private static final String PRIVATE_KEY_PROPERTY = "privateKeyBase64";
  private static final String PUBLIC_KEY_PROPERTY = "publicKeyBase64";
  private static final String ALGORITHM_PROPERTY = "algorithm";
  private static final String ED25519 = "Ed25519";

  private final Map<String, String> environment;
  private final Properties systemProperties;
  private final String directoryOverride;
  private final String keyIdPrefixOverride;

  private final Object loadLock = new Object();

  private volatile SigningIdentity cachedIdentity;

  public LocalPersistentSigningIdentitySource() {
    this(System.getenv(), System.getProperties(), null, null);
  }

  public LocalPersistentSigningIdentitySource(Properties systemProperties) {
    this(System.getenv(), systemProperties, null, null);
  }

  public LocalPersistentSigningIdentitySource(String directoryPath) {
    this(System.getenv(), System.getProperties(), directoryPath, null);
  }

  public LocalPersistentSigningIdentitySource(String directoryPath, String keyIdPrefix) {
    this(System.getenv(), System.getProperties(), directoryPath, keyIdPrefix);
  }

  LocalPersistentSigningIdentitySource(
      Map<String, String> environment,
      Properties systemProperties,
      String directoryOverride,
      String keyIdPrefixOverride) {
    this.environment = environment;
    this.systemProperties = systemProperties;
    this.directoryOverride = directoryOverride;
    this.keyIdPrefixOverride = keyIdPrefixOverride;
  }

  @Override
  public SigningIdentity currentIdentity() {
    SigningIdentity current = cachedIdentity;
    if (current != null) {
      return current;
    }
    synchronized (loadLock) {
      if (cachedIdentity != null) {
        return cachedIdentity;
      }
      cachedIdentity = loadOrCreateIdentity(resolveIdentityFile());
      return cachedIdentity;
    }
  }

  private SigningIdentity loadOrCreateIdentity(Path identityFile) {
    Path directory = identityFile.getParent();
    if (directory == null) {
      throw new IllegalStateException(
          "Managed local signing identity path has no parent directory: " + identityFile);
    }
    ensureDirectory(directory);
    if (Files.exists(identityFile)) {
      if (Files.isDirectory(identityFile)) {
        throw new IllegalStateException(
            "Managed local signing identity path is a directory, expected a file: "
                + identityFile.toAbsolutePath());
      }
      return loadIdentity(identityFile);
    }
    return createAndPersistIdentity(identityFile);
  }

  private SigningIdentity createAndPersistIdentity(Path identityFile) {
    SigningIdentity generated = generateIdentity(resolveKeyIdPrefix());
    Path tempFile = identityFile.resolveSibling(IDENTITY_FILENAME + ".tmp-" + UUID.randomUUID());
    try {
      writeIdentityFile(tempFile, generated);
      try {
        Files.move(tempFile, identityFile, StandardCopyOption.ATOMIC_MOVE);
      } catch (FileAlreadyExistsException e) {
        Files.deleteIfExists(tempFile);
        return loadIdentity(identityFile);
      } catch (IOException atomicMoveFailure) {
        try {
          Files.move(tempFile, identityFile);
        } catch (FileAlreadyExistsException e) {
          Files.deleteIfExists(tempFile);
          return loadIdentity(identityFile);
        }
      }
      applyFilePermissions(identityFile);
      return generated;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to persist managed local signing identity: " + identityFile.toAbsolutePath(), e);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        // best-effort cleanup
      }
    }
  }

  private SigningIdentity loadIdentity(Path identityFile) {
    Properties properties = new Properties();
    try {
      String raw = Files.readString(identityFile, StandardCharsets.UTF_8);
      if (raw.isBlank()) {
        throw new IllegalStateException(
            "Managed local signing identity file is blank: " + identityFile.toAbsolutePath());
      }
      properties.load(new StringReader(raw));
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read managed local signing identity file: " + identityFile.toAbsolutePath(),
          e);
    }

    String keyId = requireProperty(properties, KEY_ID_PROPERTY, identityFile);
    String privateKey = requireProperty(properties, PRIVATE_KEY_PROPERTY, identityFile);
    String publicKey = requireProperty(properties, PUBLIC_KEY_PROPERTY, identityFile);
    String algorithm = requireProperty(properties, ALGORITHM_PROPERTY, identityFile);
    if (!ED25519.equals(algorithm)) {
      throw new IllegalStateException(
          "Managed local signing identity file uses unsupported algorithm '"
              + algorithm
              + "': "
              + identityFile.toAbsolutePath());
    }
    return SigningIdentity.ed25519(keyId, privateKey, publicKey);
  }

  private void writeIdentityFile(Path identityFile, SigningIdentity identity) throws IOException {
    Properties properties = new Properties();
    properties.setProperty(KEY_ID_PROPERTY, identity.getKeyId());
    properties.setProperty(PRIVATE_KEY_PROPERTY, identity.getPrivateKeyBase64());
    properties.setProperty(PUBLIC_KEY_PROPERTY, identity.getPublicKeyBase64());
    properties.setProperty(ALGORITHM_PROPERTY, identity.getAlgorithm());

    StringWriter writer = new StringWriter();
    properties.store(writer, "Managed TaktX signing identity");
    Files.writeString(identityFile, writer.toString(), StandardCharsets.UTF_8);
    applyFilePermissions(identityFile);
  }

  private static String requireProperty(Properties properties, String key, Path identityFile) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Managed local signing identity file is missing required property '"
              + key
              + "': "
              + identityFile.toAbsolutePath());
    }
    return value.trim();
  }

  private SigningIdentity generateIdentity(String keyIdPrefix) {
    KeyPair keyPair = SigningKeyGenerator.generate();
    return SigningIdentity.ed25519(
        keyIdPrefix + UUID.randomUUID(),
        SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
        SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
  }

  private Path resolveIdentityFile() {
    return resolveDirectory().resolve(IDENTITY_FILENAME);
  }

  private Path resolveDirectory() {
    String configuredDirectory =
        firstNonBlank(
            directoryOverride,
            environment.get(DIRECTORY_ENV_VAR),
            systemProperties.getProperty(DIRECTORY_SYS_PROP));
    if (configuredDirectory != null) {
      return Path.of(configuredDirectory);
    }
    String userHome = systemProperties.getProperty("user.home");
    if (userHome == null || userHome.isBlank()) {
      throw new IllegalStateException(
          "No managed local signing directory configured and user.home is unavailable. "
              + "Set "
              + DIRECTORY_SYS_PROP
              + " or "
              + DIRECTORY_ENV_VAR
              + ".");
    }
    return Path.of(userHome, ".taktx", "signing");
  }

  private String resolveKeyIdPrefix() {
    return firstNonBlank(
        keyIdPrefixOverride,
        environment.get(KEY_ID_PREFIX_ENV_VAR),
        systemProperties.getProperty(KEY_ID_PREFIX_SYS_PROP),
        DEFAULT_KEY_ID_PREFIX);
  }

  private static void ensureDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      applyDirectoryPermissions(directory);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create managed local signing directory: " + directory.toAbsolutePath(), e);
    }
  }

  private static void applyDirectoryPermissions(Path directory) {
    applyPosixPermissions(directory, PosixFilePermissions.fromString("rwx------"));
  }

  private static void applyFilePermissions(Path file) {
    applyPosixPermissions(file, PosixFilePermissions.fromString("rw-------"));
  }

  private static void applyPosixPermissions(Path path, Set<PosixFilePermission> permissions) {
    try {
      if (!Files.getFileStore(path).supportsFileAttributeView("posix")) {
        return;
      }
      Files.setPosixFilePermissions(path, permissions);
    } catch (IOException ignored) {
      // best-effort hardening only
    }
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }
}


