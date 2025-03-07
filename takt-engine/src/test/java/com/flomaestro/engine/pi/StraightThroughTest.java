package com.flomaestro.engine.pi;

import com.flomaestro.engine.pi.testengine.SingletonBpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StraightThroughTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testStraighThrough() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ExternalTask_1")
        .andRespondWithSuccess(VariablesDTO.empty())
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
