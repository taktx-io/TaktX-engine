/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.license;

public class LicenseFeatures {
  private LicenseFeatures() {
    // Prevent instantiation
  }

  public static final String LICENSE_FEATURE_EXPIRY_DATE = "expiryDate";

  // Fields used in licenses pushed via taktx-configuration topic
  public static final String LICENSE_FEATURE_LICENSE_TYPE = "licenseType";

  /** Canonical field name as emitted by the TaktX license tool. Zero means unlimited. */
  public static final String LICENSE_FEATURE_PARTITION_BUDGET = "partitionBudget";
}
