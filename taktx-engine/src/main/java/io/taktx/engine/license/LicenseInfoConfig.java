/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.1.
 *  * Free non-production use is permitted with up to 3 Kafka partitions.
 *  * Production use is prohibited without a commercial license.
 *  * See LICENSE.md file for complete terms and conditions.
 *  * For commercial licensing, contact [info@taktx.io] or visit [https://www.taktx.io/contact].
 *
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
        """
        TaktX Engine vX.X.X - Copyright (c) 2025 TaktX B.V. All rights reserved.
        Licensed under TaktX Business Source License v1.1.

        TERMS SUMMARY:
        - Free use (including production) limited to 3 Kafka partitions per topic used by the engine
        - This version will be available under Apache License 2.0 in four years
        - No warranty or liability (provided "AS IS")

        Full license terms at [https://taktx.io/license]
        For commercial licensing, contact us at [https://taktx.io/contact]
        """;

    // Print to console with clear formatting
    System.out.println(taktxBanner);
    System.out.println(separator);
    System.out.println("LICENSE INFORMATION");
    System.out.println(separator);
    System.out.println(licenseInfo);

    if (licenseManager.isLicenseValid()) {
      System.out.println("✅ Valid commercial license found: " + licenseManager.getLicenseInfo());
    } else {
      System.out.println("⚠️ No valid commercial license found. Running with limitations:");
      System.out.println("  • Maximum 3 Kafka partitions");
      System.out.println("  • Premium features disabled");
      System.out.println(
          "For commercial use and to unlock premium features, contact us at [https://taktx.io/contact].");
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
      log.info("Valid commercial license found: {}", licenseManager.getLicenseInfo());
    } else {
      log.warn(
          """
              ⚠️ No valid commercial license found. Running with limitations:
                • Maximum 3 Kafka partitions
                • Premium features disabled
              For commercial use and to unlock premium features, contact us at [https://taktx.io/contact].
              """);
    }
  }
}
