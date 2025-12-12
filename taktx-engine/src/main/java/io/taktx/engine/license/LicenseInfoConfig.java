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
        Alpha version

        TERMS SUMMARY:
        - Free use for testing and evaluation purposes
        - Use in production is allowed but features and performance may be limited
        - No warranty or liability (provided "AS IS")

        Full license terms at [https://taktx.io/license]
        For commercial licensing, contact us at [https://taktx.io/contact]
        """,
            version);

    String licenseCheckMessage =
        switch (licenseManager.getLicenseState()) {
          case VALID -> "✅ Valid license found: " + licenseManager.getLicenseInfo();
          case EXPIRED ->
              "⚠️ Warning: Your license has expired. Please renew at https://taktx.io/contact";
          case INVALID ->
              "❌ Error: Your license is invalid, did somebody tamper with it? Engine will not start. Please contact support at https://taktx.io/contact";
          case NOT_FOUND ->
              "⚠️ Warning: No license file found. Features & performance may be limited.";
          default -> "";
        };

    // Print to console with clear formatting
    System.out.println(taktxBanner);
    System.out.println(separator);
    System.out.println("LICENSE INFORMATION");
    System.out.println(separator);
    System.out.println(licenseInfo);
    System.out.println(separator);
    System.out.println(licenseCheckMessage);
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
            + licenseInfo
            + separator
            + "\n"
            + licenseCheckMessage
            + "\n");
    if (licenseManager.getLicenseState() == LicenseState.INVALID) {
      // Exit the application if we don't have a valid license
      Runtime.getRuntime().halt(1);
    }
  }
}
