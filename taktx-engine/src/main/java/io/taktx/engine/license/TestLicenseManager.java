/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
  public void updateFromLicensePush(String licenseType, Integer partitionBudget) {
    // no-op in test — test profile uses fixed values above
  }
}
