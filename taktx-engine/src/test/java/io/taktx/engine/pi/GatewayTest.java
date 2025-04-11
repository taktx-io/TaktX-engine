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
class GatewayTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testParallelGateway() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/gateway-parallel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasInstantiatedElementWithId("EndEvent_1", 1);
  }

  @Test
  void testInclusiveGateway_DefaultFlow() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(1)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_3")
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(2)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(3)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep3() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(
            VariablesDTO.of("inputVariable", 2, "inputVariable2", "a", "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("Task_3");
  }

  @Test
  void testExclusiveGatewy() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/sequence-flow-condition.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("EndEvent_1")

        // now test the alternative default flow
        .toProcessLevel()
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  private static class DummyObject {

    private final int i;

    public DummyObject(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }
  }
}
