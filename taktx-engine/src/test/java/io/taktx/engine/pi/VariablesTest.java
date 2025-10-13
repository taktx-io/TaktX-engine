/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class VariablesTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessServiceTaskSingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithSuccess("service-task", VariablesDTO.of("var2", "value2"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasVariableWithValue("var2", "value2");
  }

  @Test
  void testProcessServiceTaskInSubProcess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "Value1"))
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.of("var2", "Value2"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("var1", "Value1")
        .hasVariableWithValue("var2", "Value2")
        .hasVariableWithValue("inputMappedVar1", "mappedValue1")
        .hasVariableWithValue("outputMappedVar2", "mappedValue1 Value2");
  }

  @Test
  void testProcessCallActivity_NoInputPropagation_WithOutputPropagation() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_scripttask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "Value1", "calledActivity", "calledActivity"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("var1", "Value1")
        .hasVariableWithValue("InputVariable", "123")
        .hasVariableWithValue("resultVar1", 123)
        .hasVariableWithValue("resultVar2", "Value1 output");
  }
}
