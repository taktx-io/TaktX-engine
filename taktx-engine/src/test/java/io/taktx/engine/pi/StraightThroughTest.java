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
class StraightThroughTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testStraighThrough() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn", "external-task")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ExternalTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .isTerminated()
        .hasTerminatedElementWithId("ExternalTask_1")
        .hasNotPassedElementWithId("Activity_0cxnpbx")
        .hasNotPassedElementWithId("Activity_1ohwsp7")
        .hasNotPassedElementWithId("Activity_09g9dzh")
        .hasNotPassedElementWithId("Event_1h5ln3k");
  }
}
