/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.license;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LicenseInfoConfig {
  private final LicenseManager licenseManager;

  void onStart(@Observes @Priority(Integer.MIN_VALUE) StartupEvent ev) {
    // resolve version from JAR manifest; fallback to "dev"
    String version = getClass().getPackage().getImplementationVersion();
    if (version == null || version.isBlank()) {
      version = "dev";
    }

    // TaktX ASCII Art Banner
    String taktxBanner =
        """
                                                                                  @@@@@@@@@@@@@@@@            \s
                                                                               @@@@@@@@@@@@@@@@@@@@@@@        \s
                                                                            @@@@@@@              @@@@@@@      \s
                                                                          @@@@@@                     @@@@@    \s
                                                                         @@@@@                         @@@@@  \s
                                                                                                        @@@@@ \s
           @@@@@@@@@@@@@@@      @@@@         @@@@@     @@@@@  @@@@@@@@@@@@@@@  ===== ====+       @@@@    @@@@@\s
           @@@@@@@@@@@@@@      @@@@@@        @@@@@    @@@@@   @@@@@@@@@@@@@@@   ===== =====    @@@@@       @@@@
                @@@@          @@@@@@@@       @@@@@   @@@@          @@@@           ====  ====  @@@@@        @@@@
                @@@@         @@@@  @@@       @@@@@ @@@@@           @@@@            ====  ====%@@@@          @@@
                @@@@         @@@@  @@@@      @@@@@@@@@@            @@@@             ===== +===#@            @@@
                @@@@        @@@@@@@@@@@@     @@@@@@@@@@            @@@@             ===== +===#@            @@@
                @@@@       @@@@@@@@@@@@@@    @@@@@ @@@@@           @@@@            ====  ====%@@@           @@@
                @@@@      @@@@       @@@@@   @@@@@   @@@@          @@@@           ====  ====  @@@@@        @@@@
                @@@@      @@@@        @@@@   @@@@@    @@@@@        @@@@         ===== =====    @@@@@       @@@@
                @@@@     @@@@          @@@@  @@@@@     @@@@@       @@@@        ===== ====+       @@@@    @@@@@\s
                                                                                                        @@@@@ \s
                                                                         @@@@@                         @@@@@  \s
                                                                          @@@@@@                     @@@@@    \s
                                                                            @@@@@@@              @@@@@@@      \s
                                                                               @@@@@@@@@@@@@@@@@@@@@@         \s
                                                                                  @@@@@@@@@@@@@@@@
        """;

    String separator = "=".repeat(80);

    String licenseInfo =
        String.format(
            """
        TaktX Engine v%s - Copyright (c) 2025 TaktX B.V. All rights reserved.
        Alpha version - NOT for production use

        TERMS SUMMARY:
        - Free use for testing and evaluation purposes
        - NO production use
        - No warranty or liability (provided "AS IS")

        Full license terms at [https://taktx.io/license]
        For commercial licensing, contact us at [https://taktx.io/contact]
        """,
            version);

    // Print to console with clear formatting
    System.out.println(taktxBanner);
    System.out.println(separator);
    System.out.println("LICENSE INFORMATION");
    System.out.println(separator);
    System.out.println(licenseInfo);

    String invalidLicenseMessage =
        "⚠️ This alpha-release requires a valid license file. Contact us at [https://taktx.io/contact].";

    if (licenseManager.isLicenseValid()) {
      System.out.println("✅ Valid license found: " + licenseManager.getLicenseInfo());
    } else {
      System.out.println(invalidLicenseMessage);
    }
    System.out.println(separator);

    // Also log through the logging system
    log.info(
        "\n"
            + taktxBanner
            + "\n"
            + separator
            + "\nLICENSE INFORMATION\n"
            + separator
            + "\n"
            + licenseInfo);
    if (licenseManager.isLicenseValid()) {
      log.info("Valid license found: {}", licenseManager.getLicenseInfo());
    } else {
      log.warn(invalidLicenseMessage);
      // Exit the application
      Runtime.getRuntime().halt(1);
    }
  }
}
