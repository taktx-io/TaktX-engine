/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class StandaloneSecurityDisabledTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void startProcess_withoutAuthorizationOrSigning_emitsUpdatesWithoutTrustMetadata()
      throws IOException {
    BpmnTestEngine engine =
        SingletonBpmnTestEngine.getInstance().deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    engine.startProcessInstance(VariablesDTO.empty());

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(engine.getConsumedInstanceUpdates()).isNotEmpty();
              assertThat(engine.getProcessInstanceMap()).isNotEmpty();
            });

    assertThat(engine.getConsumedInstanceUpdates())
        .extracting(InstanceUpdateRecord::getUpdate)
        .allMatch(update -> update.getCommandTrustMetadata() == null);
  }
}
