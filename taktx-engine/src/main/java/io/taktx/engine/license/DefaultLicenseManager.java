/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.license;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.runtime.Startup;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.LicenseReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@UnlessBuildProfile("test")
@ApplicationScoped
@Startup
@Slf4j
@RequiredArgsConstructor
public class DefaultLicenseManager implements LicenseManager {

  private static final byte[] PUBLIC_KEY_BYTES = {
    (byte) 0x52,
    (byte) 0x53,
    (byte) 0x41,
    (byte) 0x00,
    (byte) 0x30,
    (byte) 0x82,
    (byte) 0x01,
    (byte) 0x22,
    (byte) 0x30,
    (byte) 0x0D,
    (byte) 0x06,
    (byte) 0x09,
    (byte) 0x2A,
    (byte) 0x86,
    (byte) 0x48,
    (byte) 0x86,
    (byte) 0xF7,
    (byte) 0x0D,
    (byte) 0x01,
    (byte) 0x01,
    (byte) 0x01,
    (byte) 0x05,
    (byte) 0x00,
    (byte) 0x03,
    (byte) 0x82,
    (byte) 0x01,
    (byte) 0x0F,
    (byte) 0x00,
    (byte) 0x30,
    (byte) 0x82,
    (byte) 0x01,
    (byte) 0x0A,
    (byte) 0x02,
    (byte) 0x82,
    (byte) 0x01,
    (byte) 0x01,
    (byte) 0x00,
    (byte) 0xAA,
    (byte) 0x53,
    (byte) 0x3C,
    (byte) 0x2E,
    (byte) 0xDF,
    (byte) 0x0A,
    (byte) 0xA2,
    (byte) 0xEB,
    (byte) 0xD9,
    (byte) 0x16,
    (byte) 0xF0,
    (byte) 0xC0,
    (byte) 0x7E,
    (byte) 0xA0,
    (byte) 0x0A,
    (byte) 0xE4,
    (byte) 0xAB,
    (byte) 0x9B,
    (byte) 0xB8,
    (byte) 0x1B,
    (byte) 0x43,
    (byte) 0x33,
    (byte) 0x03,
    (byte) 0xEA,
    (byte) 0x76,
    (byte) 0x24,
    (byte) 0x83,
    (byte) 0x9E,
    (byte) 0x79,
    (byte) 0xA5,
    (byte) 0x5D,
    (byte) 0xAE,
    (byte) 0x66,
    (byte) 0xD1,
    (byte) 0x2A,
    (byte) 0x05,
    (byte) 0x04,
    (byte) 0x8E,
    (byte) 0xD1,
    (byte) 0xA0,
    (byte) 0x0D,
    (byte) 0xC0,
    (byte) 0x4A,
    (byte) 0x45,
    (byte) 0x32,
    (byte) 0x4C,
    (byte) 0x7C,
    (byte) 0x88,
    (byte) 0xFC,
    (byte) 0xF6,
    (byte) 0x54,
    (byte) 0x85,
    (byte) 0xB9,
    (byte) 0xC5,
    (byte) 0xCA,
    (byte) 0x44,
    (byte) 0x41,
    (byte) 0xEE,
    (byte) 0x9B,
    (byte) 0x56,
    (byte) 0x86,
    (byte) 0x08,
    (byte) 0x1D,
    (byte) 0x6A,
    (byte) 0x21,
    (byte) 0x88,
    (byte) 0xA0,
    (byte) 0x44,
    (byte) 0x9D,
    (byte) 0xD1,
    (byte) 0xF5,
    (byte) 0x4B,
    (byte) 0xE5,
    (byte) 0x69,
    (byte) 0x07,
    (byte) 0x64,
    (byte) 0x97,
    (byte) 0x01,
    (byte) 0x03,
    (byte) 0x61,
    (byte) 0x51,
    (byte) 0xAB,
    (byte) 0xE9,
    (byte) 0x64,
    (byte) 0xD1,
    (byte) 0xAB,
    (byte) 0x21,
    (byte) 0xF1,
    (byte) 0x26,
    (byte) 0x46,
    (byte) 0x35,
    (byte) 0xDB,
    (byte) 0x4A,
    (byte) 0x20,
    (byte) 0x2E,
    (byte) 0xD8,
    (byte) 0x67,
    (byte) 0xA3,
    (byte) 0x5D,
    (byte) 0xCB,
    (byte) 0xD9,
    (byte) 0xEA,
    (byte) 0x14,
    (byte) 0xB6,
    (byte) 0xE5,
    (byte) 0x14,
    (byte) 0x14,
    (byte) 0xDE,
    (byte) 0x89,
    (byte) 0xBE,
    (byte) 0x57,
    (byte) 0x6A,
    (byte) 0x38,
    (byte) 0x5C,
    (byte) 0x55,
    (byte) 0x73,
    (byte) 0x35,
    (byte) 0xA4,
    (byte) 0x20,
    (byte) 0xC7,
    (byte) 0x19,
    (byte) 0x98,
    (byte) 0x16,
    (byte) 0x61,
    (byte) 0x37,
    (byte) 0x4E,
    (byte) 0x5E,
    (byte) 0xED,
    (byte) 0x6B,
    (byte) 0x28,
    (byte) 0x3F,
    (byte) 0xA6,
    (byte) 0x45,
    (byte) 0x92,
    (byte) 0xCE,
    (byte) 0xF9,
    (byte) 0xF3,
    (byte) 0x17,
    (byte) 0xF1,
    (byte) 0x0A,
    (byte) 0xBA,
    (byte) 0x43,
    (byte) 0x1D,
    (byte) 0x78,
    (byte) 0x2D,
    (byte) 0x60,
    (byte) 0xC7,
    (byte) 0x1A,
    (byte) 0x6C,
    (byte) 0x24,
    (byte) 0xBA,
    (byte) 0xCA,
    (byte) 0x84,
    (byte) 0x53,
    (byte) 0x5A,
    (byte) 0x30,
    (byte) 0x8E,
    (byte) 0x11,
    (byte) 0xEF,
    (byte) 0x18,
    (byte) 0xC5,
    (byte) 0x40,
    (byte) 0x88,
    (byte) 0xC0,
    (byte) 0xED,
    (byte) 0x41,
    (byte) 0x2D,
    (byte) 0xAA,
    (byte) 0xBB,
    (byte) 0x5E,
    (byte) 0x70,
    (byte) 0x44,
    (byte) 0x00,
    (byte) 0x3F,
    (byte) 0x0D,
    (byte) 0x9B,
    (byte) 0xDC,
    (byte) 0x61,
    (byte) 0xD1,
    (byte) 0xB4,
    (byte) 0x7F,
    (byte) 0x32,
    (byte) 0x3F,
    (byte) 0xF2,
    (byte) 0xCC,
    (byte) 0xBD,
    (byte) 0x61,
    (byte) 0x65,
    (byte) 0x5E,
    (byte) 0x5C,
    (byte) 0xD7,
    (byte) 0xDA,
    (byte) 0x30,
    (byte) 0x35,
    (byte) 0x37,
    (byte) 0x8E,
    (byte) 0xCD,
    (byte) 0x8C,
    (byte) 0xBB,
    (byte) 0xDB,
    (byte) 0xFA,
    (byte) 0xCB,
    (byte) 0x45,
    (byte) 0x13,
    (byte) 0x2B,
    (byte) 0x77,
    (byte) 0xE1,
    (byte) 0xB3,
    (byte) 0x92,
    (byte) 0xD0,
    (byte) 0xDF,
    (byte) 0xFA,
    (byte) 0xFE,
    (byte) 0xC3,
    (byte) 0xF8,
    (byte) 0xBC,
    (byte) 0xCA,
    (byte) 0x63,
    (byte) 0x13,
    (byte) 0x9E,
    (byte) 0x8A,
    (byte) 0xED,
    (byte) 0xFE,
    (byte) 0xAA,
    (byte) 0xD6,
    (byte) 0xE4,
    (byte) 0x2B,
    (byte) 0xA6,
    (byte) 0xA7,
    (byte) 0x05,
    (byte) 0xA6,
    (byte) 0x35,
    (byte) 0xFB,
    (byte) 0xEB,
    (byte) 0x96,
    (byte) 0xC5,
    (byte) 0x4B,
    (byte) 0x02,
    (byte) 0x92,
    (byte) 0x67,
    (byte) 0x28,
    (byte) 0x40,
    (byte) 0xCD,
    (byte) 0x37,
    (byte) 0x20,
    (byte) 0x1D,
    (byte) 0xF9,
    (byte) 0xE4,
    (byte) 0x99,
    (byte) 0x8D,
    (byte) 0x73,
    (byte) 0x96,
    (byte) 0x29,
    (byte) 0x93,
    (byte) 0x51,
    (byte) 0xA7,
    (byte) 0x02,
    (byte) 0x03,
    (byte) 0x01,
    (byte) 0x00,
    (byte) 0x01
  };

  private final TaktConfiguration taktConfiguration;

  // ── Single source of truth for all license state ─────────────────────────────────────────────
  // Replaced atomically at startup (from file) or when a verified license is pushed via the
  // taktx-configuration topic. Integer.MAX_VALUE means "unlimited" — TaktX is open source and
  // imposes no partition restrictions by default; SaaS deployments push a license with a budget.
  private record ResolvedLicense(LicenseState state, int partitionBudget, String info) {}

  private final AtomicReference<ResolvedLicense> resolved =
      new AtomicReference<>(
          new ResolvedLicense(
              LicenseState.NOT_FOUND,
              Integer.MAX_VALUE,
              "No license — running open source with unlimited partitions"));

  @PostConstruct
  public void init() {
    try {
      int i = Runtime.getRuntime().availableProcessors();
      // Monitor thread count
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      int threadCount = threadBean.getThreadCount();
      log.info("Available processors {} threads {} ", i, threadCount);
      loadLicense();
    } catch (Exception e) {
      log.warn("No valid license found: {}", e.getMessage());
      // loadLicense() sets the resolved state before throwing for known failures (EXPIRED,
      // INVALID).
      // Only fall back to INVALID if no specific failure state was recorded yet.
      if (resolved.get().state() == LicenseState.NOT_FOUND) {
        resolved.set(
            new ResolvedLicense(
                LicenseState.INVALID, Integer.MAX_VALUE, "License error: " + e.getMessage()));
      }
    }
  }

  private void loadLicense() throws LicenseException {
    Path licenseFilePath = taktConfiguration.getLicenseFilePath();
    File licenseFile = licenseFilePath.toFile();
    log.info("Checking for license file at " + licenseFile.getAbsolutePath());
    if (!licenseFile.exists()) {
      // resolved already holds NOT_FOUND default — nothing to do
      return;
    }
    if (licenseFile.isDirectory()) {
      log.warn(
          "License path '{}' resolves to a directory, not a file. "
              + "A Docker volume may be mounted there. Treating as NOT_FOUND.",
          licenseFile.getAbsolutePath());
      // resolved already holds NOT_FOUND default — nothing to do
      return;
    }
    log.info("License file found at " + licenseFile.getAbsolutePath());

    try (var reader = new LicenseReader(licenseFile)) {
      License lic = reader.read(IOFormat.STRING);

      if (!lic.isOK(PUBLIC_KEY_BYTES)) {
        System.out.println(
            "❌ License file not valid according key. Has the license file been tampered with?");
        resolved.set(
            new ResolvedLicense(
                LicenseState.INVALID,
                Integer.MAX_VALUE,
                "License signature invalid — file may have been tampered with"));
        // Exit the application
        Runtime.getRuntime().halt(1);
        throw new LicenseException(
            "License file not valid according key. Has the license file been tampered with?");
      }

      Feature feature = lic.getFeatures().get(LicenseFeatures.LICENSE_FEATURE_EXPIRY_DATE);
      Date expirationDate = feature.getDate();
      if (new Date().after(expirationDate)) {
        resolved.set(
            new ResolvedLicense(
                LicenseState.EXPIRED, Integer.MAX_VALUE, lic.getFeatures().toString()));
        throw new LicenseException("License expired on " + expirationDate);
      }

      Integer rawBudget = getFeatureInt(lic, LicenseFeatures.LICENSE_FEATURE_PARTITION_BUDGET);
      int budget = (rawBudget == null || rawBudget == 0) ? Integer.MAX_VALUE : rawBudget;
      resolved.set(new ResolvedLicense(LicenseState.VALID, budget, lic.getFeatures().toString()));
    } catch (IOException e) {
      throw new LicenseException("Error reading license file " + e);
    }
  }

  @Override
  public LicenseState getLicenseState() {
    return resolved.get().state();
  }

  @Override
  public String getLicenseInfo() {
    return resolved.get().info();
  }

  @Override
  public int getPartitionBudget() {
    return resolved.get().partitionBudget();
  }

  @Override
  public void updateFromLicensePush(String licenseType, int partitionBudget) {
    String info =
        String.format(
            "Pushed license: type=%s, partitions=%s",
            licenseType, partitionBudget == Integer.MAX_VALUE ? "unlimited" : partitionBudget);
    resolved.set(new ResolvedLicense(LicenseState.VALID, partitionBudget, info));
    log.info(
        "License updated from configuration topic: type={} partitionBudget={}",
        licenseType,
        partitionBudget == Integer.MAX_VALUE ? "unlimited" : partitionBudget);
  }

  /**
   * Parses a raw License3j plain-text string (as pushed to the {@code taktx-configuration} topic),
   * verifies its signature against the embedded public key, and calls {@link
   * #updateFromLicensePush} if valid.
   *
   * <p>Called by the topology's global-store processor on the Kafka Streams GlobalStreamThread.
   * Verification failures are logged as warnings and the update is skipped — the existing
   * file-based license remains active.
   *
   * @param licenseText raw License3j plain-text content (UTF-8)
   */
  @Override
  public void parsePushedLicense(String licenseText) {
    if (licenseText == null || licenseText.isBlank()) {
      log.warn("Received empty license text via configuration topic — ignoring");
      return;
    }
    try (var reader =
        new LicenseReader(new ByteArrayInputStream(licenseText.getBytes(StandardCharsets.UTF_8)))) {
      License pushed = reader.read(IOFormat.STRING);

      if (!pushed.isOK(PUBLIC_KEY_BYTES)) {
        log.warn(
            "⚠️ Pushed license failed signature verification — ignoring. "
                + "Has the license been tampered with?");
        return;
      }

      // Check expiry
      Feature expiryFeature = pushed.getFeatures().get(LicenseFeatures.LICENSE_FEATURE_EXPIRY_DATE);
      if (expiryFeature != null) {
        Date expiryDate = expiryFeature.getDate();
        if (new Date().after(expiryDate)) {
          log.warn("⚠️ Pushed license is expired (expiry={}) — ignoring", expiryDate);
          return;
        }
      }

      // Extract fields — only partition budget is enforced as a license gate.
      // The license tool uses 0 to mean "unlimited" — map that to Integer.MAX_VALUE.
      String licenseType = getFeatureString(pushed, LicenseFeatures.LICENSE_FEATURE_LICENSE_TYPE);
      Integer rawBudget = getFeatureInt(pushed, LicenseFeatures.LICENSE_FEATURE_PARTITION_BUDGET);
      int partitionBudget = (rawBudget == null || rawBudget == 0) ? Integer.MAX_VALUE : rawBudget;

      updateFromLicensePush(licenseType, partitionBudget);
    } catch (IOException e) {
      log.warn("Failed to read pushed license text: {}", e.getMessage());
    } catch (Exception e) {
      log.warn("Unexpected error parsing pushed license: {}", e.getMessage(), e);
    }
  }

  static String getFeatureString(License lic, String... names) {
    Feature f = getFirstFeature(lic, names);
    return f != null ? f.getString() : null;
  }

  static Integer getFeatureInt(License lic, String... names) {
    Feature f = getFirstFeature(lic, names);
    if (f == null) return null;
    try {
      return f.getInt();
    } catch (Exception _) {
      try {
        return Integer.parseInt(f.getString().trim());
      } catch (Exception _) {
        return null;
      }
    }
  }

  private static Feature getFirstFeature(License lic, String... names) {
    if (lic == null || names == null) {
      return null;
    }
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Feature feature = lic.getFeatures().get(name);
      if (feature != null) {
        return feature;
      }
    }
    return null;
  }
}
