/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.integration;

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
        // var2 is returned by the worker but is NOT declared in the explicit output mappings of
        // ServiceTask_1. Per Zeebe semantics: explicit output mappings → only mapped variables
        // survive task completion. Unmapped locals are dropped.
        .hasVariableWithValue("MappedOutputVariable", "value1");
  }

  @Test
  void tesSettVariablesRootScope() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitForExternalTaskTrigger("service-task")
        .setVariablesAtRootScope(VariablesDTO.of("rootVar", "rootValue"))
        .andRespondToExternalTaskWithSuccess("service-task", VariablesDTO.of("var2", "value2"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("rootVar", "rootValue");
  }

  @Test
  void testSetVariablesElementScope() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitForExternalTaskTrigger("servicetask")
        // SubTask_1 has explicit output mappings, so variables set in its local scope are dropped
        // on completion. Set the variable at the SubProcess_1 scope instead: the subprocess has
        // no output mappings, so all dirty variables there propagate to the root scope.
        .setVariablesForElement("SubProcess_1", VariablesDTO.of("rootVar", "rootValue"))
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.of("var2", "value2"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("rootVar", "rootValue");
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
        // var2 and inputMappedVar1 are local to SubTask_1, which has explicit output mappings.
        // Per Zeebe semantics only the declared output "outputMappedVar2" survives; the
        // unmapped locals (var2, inputMappedVar1) are dropped when the task completes.
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
        // resultVar1 (=123) is the script result variable, stored in the script task's local scope.
        // The task has an explicit output mapping ("resultVar2"), so unmapped locals are dropped
        // per Zeebe semantics. resultVar1 never reaches the called-process or parent scope.
        .hasVariableWithValue("resultVar2", "Value1 output");
  }
}
