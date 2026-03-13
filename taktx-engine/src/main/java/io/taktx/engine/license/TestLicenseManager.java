/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.license;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

@IfBuildProfile("test")
@ApplicationScoped
public class TestLicenseManager implements LicenseManager {

  @Override
  public LicenseState getLicenseState() {
    return LicenseState.VALID;
  }

  @Override
  public String getLicenseInfo() {
    return "Test License";
  }

  @Override
  public int getPartitionBudget() {
    return 0; // no budget cap in tests
  }

  @Override
  public boolean isEventSigningAllowed() {
    return true;
  }

  @Override
  public void updateFromLicensePush(
      String licenseType, Integer maxKafkaPartitions, boolean eventSigning) {
    // no-op in test — test profile uses fixed values above
  }
}
