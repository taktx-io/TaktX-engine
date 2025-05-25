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
class ErrorsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testInterruptingErrorTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithFailure(
            false, "456", "message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_WithErrorReference")
        .hasInstantiatedElementWithId("EndEvent_Error_WithErrorReference")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingError_CatchAllTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithFailure(
            false, "Error_1tlo99v", "message", VariablesDTO.of("var1", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingError_NoCode_CatchAllTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithFailure(false, null, null, VariablesDTO.of("var1", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingErrorTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithFailure(
            false, "456", "error message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_Reference")
        .hasInstantiatedElementWithId("EndEvent_Reference");
  }

  @Test
  void testCatchAllErrorTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithFailure(
            false, "Error_1tlo99v", "error message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference");
  }

  @Test
  void testNoErrorTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn", "servicetask")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("Subprocess_1/SubStartEvent")
        .hasInstantiatedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasInstantiatedElementWithId("Subprocess_1/SubEndEvent")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference")
        .hasVariableWithValue("var1", "value1");
  }
}
