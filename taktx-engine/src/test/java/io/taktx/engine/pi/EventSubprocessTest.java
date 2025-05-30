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
class EventSubprocessTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void test_EventSubProcess_ErrorTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn", "ServiceTask_1")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithFailure(
            false, "errorCode", "error message", VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_02s4c8o")
        .hasPassedElementWithId("Activity_02s4c8o/Event_1krfnik", 1)
        .hasPassedElementWithId("Activity_02s4c8o/Activity_0haxijj", 1)
        .hasPassedElementWithId("Activity_02s4c8o/Event_03m37d5", 1);
  }

  @Test
  void test_EventSubProcess_ErrorTriggered_catchAll() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn", "ServiceTask_1")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithFailure(false, "1234", "error message", VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_0ulltxs")
        .hasPassedElementWithId("Activity_0ulltxs/Event_0gfl68s", 1)
        .hasPassedElementWithId("Activity_0ulltxs/Activity_01prqev", 1)
        .hasPassedElementWithId("Activity_0ulltxs/Event_0as9m6v", 1);
  }

  @Test
  void test_EventSubProcess_EscalationTriggered_Catchall() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn", "ServiceTask_1")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "escalationCode", "escalation message", VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_0w3didi")
        .hasPassedElementWithId("Activity_0w3didi/Event_1x06bbg", 1)
        .hasPassedElementWithId("Activity_0w3didi/Activity_06z7g1l", 1)
        .hasPassedElementWithId("Activity_0w3didi/Event_11ddhgq", 1);
  }

  @Test
  void test_EventSubProcess_EscalationTriggered_NonInterrupting() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn", "ServiceTask_1")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation("1234", "error message", VariablesDTO.empty())
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("ServiceTask_1", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasInstantiatedElementWithId("Activity_1jz01tr")
        .hasPassedElementWithId("Activity_1jz01tr/Event_0utmfy5", 1)
        .hasPassedElementWithId("Activity_1jz01tr/Activity_0xpyuez", 1)
        .hasPassedElementWithId("Activity_1jz01tr/Event_1ffpqj3", 1);
  }
}
