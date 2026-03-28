/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.client.annotation.Deployment;
import java.util.Set;
import org.junit.jupiter.api.Test;

@Deployment(resources = "test")
class AnnotationScannerTest {

  @Test
  void testScan() {
    Set<Deployment> taktDeployments = AnnotationScanner.findTaktDeployments();
    assertThat(taktDeployments).hasSize(1);

    Deployment taktDeployment = taktDeployments.iterator().next();
    assertThat(taktDeployment.resources()).containsExactly("test");
  }
}
