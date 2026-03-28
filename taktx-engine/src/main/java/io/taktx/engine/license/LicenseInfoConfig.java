/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
        TaktX Engine v%s - Copyright (c) 2025 Eric Hendriks

        Licensed under the Apache License, Version 2.0.
        https://www.apache.org/licenses/LICENSE-2.0

        TERMS SUMMARY:
        - Free to use, modify, and distribute under the Apache License 2.0
        - All features are fully enabled (signing, JWT auth, etc.)
        - A default partition budget applies; a license pushed via the
          taktx-configuration topic can extend this limit
        - No warranty or liability (provided "AS IS")

        Full license terms at https://taktx.io/license
        For enterprise features and support, contact https://taktx.io/contact
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
              "ℹ️ No license file found. Running with default partition budget. All features are fully enabled.";
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
