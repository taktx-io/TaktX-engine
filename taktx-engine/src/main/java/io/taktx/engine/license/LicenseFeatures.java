/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.license;

public class LicenseFeatures {
  private LicenseFeatures() {
    // Prevent instantiation
  }

  public static final String LICENSE_FEATURE_EXPIRY_DATE = "expiryDate";
  public static final String LICENSE_FEATURE_PARTITION_LIMIT = "partitionLimit";
}
