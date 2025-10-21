/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.license;

import io.quarkus.runtime.Startup;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.Date;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.LicenseReader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Startup
@Slf4j
@RequiredArgsConstructor
public class LicenseManager {

  public static final int DEFAULT_PARTITION_LIMIT = 3;

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

  private License license;
  private final TaktConfiguration taktConfiguration;

  @Getter private boolean licenseValid = false;

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
      log.warn("No valid license found." + e.getMessage());
      licenseValid = false;
    }
  }

  private void loadLicense() throws LicenseException {
    File licenseFile = Path.of(taktConfiguration.getLicenseFileLocation()).toFile();
    log.info("Checking for license file at " + licenseFile.getAbsolutePath());
    if (!licenseFile.exists()) {
      // Exit the application
      throw new LicenseException("License file not found");
    } else {
      log.info("License file found at " + licenseFile.getAbsolutePath());
    }

    try (var reader = new LicenseReader(licenseFile)) {
      license = reader.read(IOFormat.STRING);

      if (!license.isOK(PUBLIC_KEY_BYTES)) {
        System.out.println(
            "❌ License file not valid according key. Has the license file been tampered with?");
        licenseValid = false;
        // Exit the application
        Runtime.getRuntime().halt(1);
        throw new LicenseException(
            "License file not valid according key. Has the license file been tampered with?");
      }

      Feature feature = license.getFeatures().get(LicenseFeatures.LICENSE_FEATURE_EXPIRY_DATE);
      Date expirationDate = feature.getDate();
      if (new Date().after(expirationDate)) {
        licenseValid = false;
        throw new LicenseException("License expired on " + expirationDate);
      }

      licenseValid = true;
    } catch (IOException e) {
      throw new LicenseException("Error reading license file " + e);
    }
  }

  public int getPartitionLimit() {
    if (!licenseValid) {
      return DEFAULT_PARTITION_LIMIT;
    }

    try {
      Feature partitionLimit =
          license.getFeatures().get(LicenseFeatures.LICENSE_FEATURE_PARTITION_LIMIT);
      if (partitionLimit == null) {
        return DEFAULT_PARTITION_LIMIT;
      }
      return partitionLimit.getInt();
    } catch (Exception e) {
      return DEFAULT_PARTITION_LIMIT;
    }
  }

  public String getLicenseInfo() {
    return license.getFeatures().toString();
  }
}
