/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads the signing identity from mounted files so key rotation can happen without restart. */
public class FileSigningIdentitySource implements SigningIdentitySource {

  public static final long DEFAULT_REFRESH_INTERVAL_MS = 1000L;

  private static final Logger log = Logger.getLogger(FileSigningIdentitySource.class.getName());

  private static final String PRIVATE_ENV_VAR = "TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH";
  private static final String PRIVATE_ENV_VAR_ALIAS = "TAKTX_SIGNING_PRIVATE_KEY_FILE";
  private static final String PRIVATE_SYS_PROP = "taktx.signing.file.private-key-path";
  private static final String PRIVATE_SYS_PROP_ALIAS = "taktx.signing.private-key-file";

  private static final String PUBLIC_ENV_VAR = "TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH";
  private static final String PUBLIC_ENV_VAR_ALIAS = "TAKTX_SIGNING_PUBLIC_KEY_FILE";
  private static final String PUBLIC_SYS_PROP = "taktx.signing.file.public-key-path";
  private static final String PUBLIC_SYS_PROP_ALIAS = "taktx.signing.public-key-file";

  private static final String KEY_ID_ENV_VAR = "TAKTX_SIGNING_FILE_KEY_ID_PATH";
  private static final String KEY_ID_ENV_VAR_ALIAS = "TAKTX_SIGNING_KEY_ID_FILE";
  private static final String KEY_ID_SYS_PROP = "taktx.signing.file.key-id-path";
  private static final String KEY_ID_SYS_PROP_ALIAS = "taktx.signing.key-id-file";

  private static final String REFRESH_INTERVAL_ENV_VAR = "TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS";
  private static final String REFRESH_INTERVAL_SYS_PROP = "taktx.signing.file.refresh-interval-ms";

  private final Map<String, String> environment;
  private final Properties systemProperties;
  private final String keyIdPathOverride;
  private final String privateKeyPathOverride;
  private final String publicKeyPathOverride;
  private final long refreshIntervalMs;
  private final LongSupplier currentTimeMillis;

  private final Object refreshLock = new Object();

  private volatile SigningIdentity cachedIdentity;
  private volatile FileState cachedFileState;
  private volatile long nextRefreshAtMs;

  public FileSigningIdentitySource() {
    this(
        System.getenv(), System.getProperties(), null, null, null, null, System::currentTimeMillis);
  }

  public FileSigningIdentitySource(String keyIdPath, String privateKeyPath, String publicKeyPath) {
    this(
        System.getenv(),
        System.getProperties(),
        keyIdPath,
        privateKeyPath,
        publicKeyPath,
        null,
        System::currentTimeMillis);
  }

  public FileSigningIdentitySource(
      String keyIdPath, String privateKeyPath, String publicKeyPath, long refreshIntervalMs) {
    this(
        System.getenv(),
        System.getProperties(),
        keyIdPath,
        privateKeyPath,
        publicKeyPath,
        refreshIntervalMs,
        System::currentTimeMillis);
  }

  public FileSigningIdentitySource(Properties systemProperties) {
    this(System.getenv(), systemProperties, null, null, null, null, System::currentTimeMillis);
  }

  FileSigningIdentitySource(
      Map<String, String> environment,
      Properties systemProperties,
      String keyIdPathOverride,
      String privateKeyPathOverride,
      String publicKeyPathOverride,
      Long refreshIntervalOverrideMs,
      LongSupplier currentTimeMillis) {
    this.environment = environment;
    this.systemProperties = systemProperties;
    this.keyIdPathOverride = keyIdPathOverride;
    this.privateKeyPathOverride = privateKeyPathOverride;
    this.publicKeyPathOverride = publicKeyPathOverride;
    this.refreshIntervalMs =
        parseRefreshInterval(
            refreshIntervalOverrideMs,
            environment.get(REFRESH_INTERVAL_ENV_VAR),
            systemProperties.getProperty(REFRESH_INTERVAL_SYS_PROP));
    this.currentTimeMillis = currentTimeMillis;
  }

  @Override
  public SigningIdentity currentIdentity() {
    long now = currentTimeMillis.getAsLong();
    SigningIdentity current = cachedIdentity;
    if (now < nextRefreshAtMs) {
      return current;
    }

    synchronized (refreshLock) {
      now = currentTimeMillis.getAsLong();
      if (now < nextRefreshAtMs) {
        return cachedIdentity;
      }
      return refreshIdentity(now);
    }
  }

  private SigningIdentity refreshIdentity(long now) {
    try {
      ResolvedPaths paths = resolveConfiguredPaths();
      if (paths == null) {
        cachedIdentity = null;
        cachedFileState = null;
        nextRefreshAtMs = nextRefreshAt(now);
        return null;
      }

      FileState fileState = captureFileState(paths);
      if (cachedIdentity != null && fileState.equals(cachedFileState)) {
        nextRefreshAtMs = nextRefreshAt(now);
        return cachedIdentity;
      }

      SigningIdentity identity = loadIdentity(paths);
      cachedIdentity = identity;
      cachedFileState = fileState;
      nextRefreshAtMs = nextRefreshAt(now);
      return identity;
    } catch (RuntimeException e) {
      nextRefreshAtMs = nextRefreshAt(now);
      if (cachedIdentity != null) {
        log.log(
            Level.WARNING,
            "Failed to refresh signing identity from files; keeping last known-good identity: "
                + e.getMessage(),
            e);
        return cachedIdentity;
      }
      throw e;
    }
  }

  private ResolvedPaths resolveConfiguredPaths() {
    String privateKeyPath =
        firstNonBlank(
            privateKeyPathOverride,
            environment.get(PRIVATE_ENV_VAR),
            environment.get(PRIVATE_ENV_VAR_ALIAS),
            systemProperties.getProperty(PRIVATE_SYS_PROP),
            systemProperties.getProperty(PRIVATE_SYS_PROP_ALIAS));
    if (privateKeyPath == null) {
      return null;
    }

    String keyIdPath =
        firstNonBlank(
            keyIdPathOverride,
            environment.get(KEY_ID_ENV_VAR),
            environment.get(KEY_ID_ENV_VAR_ALIAS),
            systemProperties.getProperty(KEY_ID_SYS_PROP),
            systemProperties.getProperty(KEY_ID_SYS_PROP_ALIAS));
    if (keyIdPath == null) {
      throw new IllegalArgumentException(
          "A signing private-key file is configured but no signing key-id file is configured. "
              + "Set TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH and TAKTX_SIGNING_FILE_KEY_ID_PATH "
              + "(or the matching taktx.signing.file.* properties) together.");
    }

    String publicKeyPath =
        firstNonBlank(
            publicKeyPathOverride,
            environment.get(PUBLIC_ENV_VAR),
            environment.get(PUBLIC_ENV_VAR_ALIAS),
            systemProperties.getProperty(PUBLIC_SYS_PROP),
            systemProperties.getProperty(PUBLIC_SYS_PROP_ALIAS));

    return new ResolvedPaths(keyIdPath, privateKeyPath, publicKeyPath);
  }

  private SigningIdentity loadIdentity(ResolvedPaths paths) {
    String privateKey = readRequiredFile(paths.privateKeyPath(), "private key");
    String keyId = readRequiredFile(paths.keyIdPath(), "key ID");
    String publicKey =
        paths.publicKeyPath() != null
            ? readRequiredFile(paths.publicKeyPath(), "public key")
            : null;
    return SigningIdentity.ed25519(keyId, privateKey, publicKey);
  }

  private FileState captureFileState(ResolvedPaths paths) {
    return new FileState(
        paths.keyIdPath(),
        fileFingerprint(paths.keyIdPath(), "key ID"),
        paths.privateKeyPath(),
        fileFingerprint(paths.privateKeyPath(), "private key"),
        paths.publicKeyPath(),
        paths.publicKeyPath() != null
            ? fileFingerprint(paths.publicKeyPath(), "public key")
            : null);
  }

  private FileFingerprint fileFingerprint(String rawPath, String label) {
    Path path = Path.of(rawPath);
    try {
      return new FileFingerprint(
          Files.getLastModifiedTime(path).toMillis(), Files.size(path), Files.exists(path));
    } catch (Exception e) {
      throw new UncheckedIOException(
          "Failed to inspect signing " + label + " file: " + path.toAbsolutePath(),
          e instanceof java.io.IOException ioException ? ioException : new java.io.IOException(e));
    }
  }

  private long nextRefreshAt(long now) {
    return refreshIntervalMs <= 0 ? now : now + refreshIntervalMs;
  }

  private static long parseRefreshInterval(Long override, String... configuredValues) {
    if (override != null) {
      return requireNonNegative(override, "refresh interval override");
    }
    String configured = firstNonBlank(configuredValues);
    if (configured == null) {
      return DEFAULT_REFRESH_INTERVAL_MS;
    }
    try {
      return requireNonNegative(Long.parseLong(configured.trim()), REFRESH_INTERVAL_SYS_PROP);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid signing file refresh interval '"
              + configured
              + "' for "
              + REFRESH_INTERVAL_SYS_PROP,
          e);
    }
  }

  private static long requireNonNegative(long value, String label) {
    if (value < 0) {
      throw new IllegalArgumentException(label + " must be >= 0");
    }
    return value;
  }

  private static String readRequiredFile(String rawPath, String label) {
    String value = readFile(rawPath, label);
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          "Signing " + label + " file is blank: " + Path.of(rawPath).toAbsolutePath());
    }
    return value;
  }

  private static String readFile(String rawPath, String label) {
    try {
      return Files.readString(Path.of(rawPath), StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      throw new UncheckedIOException(
          "Failed to read signing " + label + " file: " + Path.of(rawPath).toAbsolutePath(),
          e instanceof java.io.IOException ioException ? ioException : new java.io.IOException(e));
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

  private record ResolvedPaths(String keyIdPath, String privateKeyPath, String publicKeyPath) {}

  private record FileFingerprint(long lastModifiedMillis, long size, boolean exists) {}

  private record FileState(
      String keyIdPath,
      FileFingerprint keyIdFingerprint,
      String privateKeyPath,
      FileFingerprint privateKeyFingerprint,
      String publicKeyPath,
      FileFingerprint publicKeyFingerprint) {}
}
