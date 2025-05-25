package io.taktx.engine.pi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class BoundaryEventsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testBoundaryTimerTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn", "service-task-id")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimerNotTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn", "service-task-id")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_2");
  }

  @Test
  void testBoundaryTimerNonInterrupting() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-timer-non-interrupting.bpmn", "service-task-id")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Interrupted_Task_1", 3)
        .hasInstantiatedElementWithId("Boundary_Timer_1", 1);
  }

  @Test
  void testBoundaryMessageInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/boundary-message.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("BoundaryMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("BoundaryEvent_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("service-task-id")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testBoundaryMessageNonInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-message-non-interrupting.bpmn", "service-task-id")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .waitForMessageSubscription("BoundaryEventMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilElementHasPassed("BoundaryEvent_1", 3)
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("service-task-id")
        .hasInstantiatedElementWithId("BoundaryEvent_1", 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_BoundaryEventEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-timer-subprocess.bpmn", "TaskDefinition", "TaskDefinition2")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Subprocess_1/Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Subprocess_1/Service_Task_2")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasTerminatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NormalEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-timer-subprocess.bpmn", "TaskDefinition", "TaskDefinition2")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_2")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_NotTriggered_Subprocess_NormalEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-timer-subprocess.bpmn", "TaskDefinition", "TaskDefinition2")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        //        .hasTerminatedElement("SubProcess_Boundary_Timer_1")
        //        .hasPassedElementWithId("SubStartEvent_1")
        //        .hasPassedElementWithId("Service_Task_1")
        //        .hasPassedElementWithId("SubEndEvent_1")
        .hasNotPassedElementWithId("Service_Task_2")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasInstantiatedElementWithId("OkTask")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NonInterrupting_BoundaryEventEnd_NoEnd()
      throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait(
            "/bpmn/boundary-timer-subprocess-noendevent.bpmn", "TaskDefinition", "TaskDefinition2")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_2")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("success", "true"))
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasTerminatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }
}
