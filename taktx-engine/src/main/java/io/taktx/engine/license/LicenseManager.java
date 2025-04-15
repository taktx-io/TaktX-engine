package io.taktx.engine.license;

import io.quarkus.runtime.Startup;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.logging.Logger;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.LicenseReader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;

@ApplicationScoped
@Startup
@Slf4j
public class LicenseManager {

  private static final Logger LOG = Logger.getLogger(LicenseManager.class.getName());

  private static final int DEFAULT_PARTITION_LIMIT = 3;
  private static final Path LICENSE_PATH =
      Paths.get(System.getProperty("user.home"), ".taktx", "license.lic");
  private static final byte[] PUBLIC_KEY_BYTES = {
    (byte) 0x52,
    (byte) 0x53,
    (byte) 0x41,
    (byte) 0x00,
    (byte) 0x30,
    (byte) 0x81,
    (byte) 0x9F,
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
    (byte) 0x81,
    (byte) 0x8D,
    (byte) 0x00,
    (byte) 0x30,
    (byte) 0x81,
    (byte) 0x89,
    (byte) 0x02,
    (byte) 0x81,
    (byte) 0x81,
    (byte) 0x00,
    (byte) 0xCB,
    (byte) 0x16,
    (byte) 0xE7,
    (byte) 0x63,
    (byte) 0x8B,
    (byte) 0x8D,
    (byte) 0xAB,
    (byte) 0x7E,
    (byte) 0x42,
    (byte) 0x7C,
    (byte) 0x8C,
    (byte) 0xC8,
    (byte) 0x89,
    (byte) 0x7E,
    (byte) 0x4C,
    (byte) 0xD2,
    (byte) 0x58,
    (byte) 0xF4,
    (byte) 0x23,
    (byte) 0x82,
    (byte) 0xD4,
    (byte) 0x80,
    (byte) 0xBC,
    (byte) 0x78,
    (byte) 0x75,
    (byte) 0x9E,
    (byte) 0xF5,
    (byte) 0x7A,
    (byte) 0x8A,
    (byte) 0x0A,
    (byte) 0xA2,
    (byte) 0xFB,
    (byte) 0xDE,
    (byte) 0xBE,
    (byte) 0xE9,
    (byte) 0x10,
    (byte) 0x92,
    (byte) 0x87,
    (byte) 0x1A,
    (byte) 0x8D,
    (byte) 0xBA,
    (byte) 0x77,
    (byte) 0x18,
    (byte) 0x9E,
    (byte) 0x96,
    (byte) 0x21,
    (byte) 0x8C,
    (byte) 0x94,
    (byte) 0x1D,
    (byte) 0x03,
    (byte) 0xCB,
    (byte) 0xE7,
    (byte) 0x8A,
    (byte) 0x87,
    (byte) 0xCF,
    (byte) 0x56,
    (byte) 0xEF,
    (byte) 0x62,
    (byte) 0x52,
    (byte) 0x3B,
    (byte) 0x6F,
    (byte) 0x50,
    (byte) 0x8A,
    (byte) 0x91,
    (byte) 0x95,
    (byte) 0xED,
    (byte) 0xDF,
    (byte) 0x60,
    (byte) 0x05,
    (byte) 0x87,
    (byte) 0xAE,
    (byte) 0xEA,
    (byte) 0x5D,
    (byte) 0x15,
    (byte) 0xF2,
    (byte) 0x64,
    (byte) 0x53,
    (byte) 0x3C,
    (byte) 0x47,
    (byte) 0x24,
    (byte) 0xA1,
    (byte) 0x30,
    (byte) 0x3B,
    (byte) 0xA5,
    (byte) 0xCE,
    (byte) 0x4B,
    (byte) 0x9C,
    (byte) 0x5B,
    (byte) 0x89,
    (byte) 0xE0,
    (byte) 0xE8,
    (byte) 0xD9,
    (byte) 0x8D,
    (byte) 0x5D,
    (byte) 0xA9,
    (byte) 0xB1,
    (byte) 0xA7,
    (byte) 0xBF,
    (byte) 0xC3,
    (byte) 0x12,
    (byte) 0xE8,
    (byte) 0x07,
    (byte) 0x89,
    (byte) 0x78,
    (byte) 0x61,
    (byte) 0x9A,
    (byte) 0x93,
    (byte) 0x3B,
    (byte) 0x98,
    (byte) 0x7D,
    (byte) 0xAE,
    (byte) 0x3D,
    (byte) 0xEF,
    (byte) 0xB0,
    (byte) 0x71,
    (byte) 0xD2,
    (byte) 0x76,
    (byte) 0xBB,
    (byte) 0x8A,
    (byte) 0xB1,
    (byte) 0xEF,
    (byte) 0x9A,
    (byte) 0xBD,
    (byte) 0x57,
    (byte) 0x95,
    (byte) 0xF1,
    (byte) 0x7C,
    (byte) 0xCF,
    (byte) 0x02,
    (byte) 0x03,
    (byte) 0x01,
    (byte) 0x00,
    (byte) 0x01,
  };

  private License license;

  @Inject AdminClient adminClient;

  @Inject TaktConfiguration taktConfiguration;

  @Getter private boolean licenseValid = false;

  @PostConstruct
  public void init() {
    try {
      loadLicense();
    } catch (Exception e) {
      LOG.warning("No valid license found. Running with restricted features: " + e.getMessage());
      licenseValid = false;
    }
  }

  private void loadLicense() throws LicenseException {
    log.info("Checking for license file at " + LICENSE_PATH.toFile().getAbsolutePath());
    File licenseFile = LICENSE_PATH.toFile();
    if (!licenseFile.exists()) {
      // Exit the application
      throw new LicenseException("License file not found");
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

      Date expirationDate = license.getFeatures().get(LicenseFeatures.EXPIRY_DATE).getDate();
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
      Feature partitionLimit = license.getFeatures().get(LicenseFeatures.PARTITION_LIMIT);
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
