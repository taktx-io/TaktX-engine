/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.license;

public interface LicenseManager {

  LicenseState getLicenseState();

  String getLicenseInfo();

  int getPartitionBudget();

  /**
   * Returns {@code true} when the active license permits event signing.
   *
   * <p>The runtime configuration topic provides the operator's request; this method is the license
   * gate. Signing is only active when both permit it.
   */
  boolean isEventSigningAllowed();

  /**
   * Returns {@code true} when the active license permits command authorization (RS256 JWT
   * validation on inbound commands).
   *
   * <p>The runtime configuration topic provides the operator's request; this method is the license
   * gate. Authorization is only active when both permit it.
   */
  boolean isCommandAuthorizationAllowed();

  /**
   * Updates the in-memory license state from a license record pushed via the {@code
   * taktx-configuration} topic (key {@code "license"}).
   *
   * <p>Called by the topology's global-store processor on the Kafka Streams GlobalStreamThread, so
   * must be thread-safe. Pushed values take precedence over file-based values.
   *
   * @param licenseType e.g. {@code "COMMUNITY"}, {@code "STANDARD"}, {@code "ENTERPRISE"}
   * @param maxKafkaPartitions {@code null} means unlimited
   * @param eventSigning whether event signing is permitted by this license
   * @param commandAuthorization whether command authorization is permitted by this license
   */
  void updateFromLicensePush(
      String licenseType,
      Integer maxKafkaPartitions,
      boolean eventSigning,
      boolean commandAuthorization);

  /**
   * Parses, verifies and applies a raw License3j plain-text string pushed via the {@code
   * taktx-configuration} topic (key {@code "license"}).
   *
   * <p>The default implementation is a no-op so that test/stub implementations do not need to
   * override it. {@link DefaultLicenseManager} provides the real implementation.
   *
   * @param licenseText raw License3j plain-text content (UTF-8)
   */
  default void parsePushedLicense(String licenseText) {
    // no-op by default
  }
}
