/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.license;

public interface LicenseManager {

  LicenseState getLicenseState();

  String getLicenseInfo();

  int getPartitionBudget();

  /**
   * Updates the in-memory license state from a license record pushed via the {@code
   * taktx-configuration} topic (key {@code "license"}).
   *
   * <p>Called by the topology's global-store processor on the Kafka Streams GlobalStreamThread, so
   * must be thread-safe. Pushed values take precedence over file-based values. Only the partition
   * budget is read from a pushed license; signing and authorization are no longer license-gated.
   *
   * @param licenseType e.g. {@code "COMMUNITY"}, {@code "STANDARD"}, {@code "ENTERPRISE"}
   * @param maxKafkaPartitions {@code null} means unlimited
   */
  void updateFromLicensePush(String licenseType, Integer maxKafkaPartitions);

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
