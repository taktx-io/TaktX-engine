package com.flomaestro.engine.pi;

import com.flomaestro.engine.pi.testengine.SingletonBpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VariablesTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessServiceTaskSingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasVariableWithValue("var2", "value2");
  }
}
