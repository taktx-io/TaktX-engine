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

  // Fields used in licenses pushed via taktx-configuration topic
  public static final String LICENSE_FEATURE_LICENSE_TYPE = "licenseType";

  /** Canonical field name as emitted by the TaktX license tool. Zero means unlimited. */
  public static final String LICENSE_FEATURE_PARTITION_BUDGET = "partitionBudget";

  public static final String LICENSE_FEATURE_EVENT_SIGNING = "eventSigning";
  public static final String LICENSE_FEATURE_COMMAND_AUTHORIZATION = "commandAuthorization";
  public static final String LICENSE_FEATURE_CUSTOM_PERMISSIONS = "customPermissions";
  public static final String LICENSE_FEATURE_RUNWAY_STORAGE_TIER = "runwayStorageTier";
  public static final String LICENSE_FEATURE_DEPLOYMENT_MODEL = "deploymentModel";
}
