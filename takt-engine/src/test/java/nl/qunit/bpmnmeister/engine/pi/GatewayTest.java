package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class GatewayTest {

  @Inject
  Clock clock;

  static BpmnTestEngine bpmnTestEngine;

  @PostConstruct
  void init() {
    if (bpmnTestEngine == null) {
      bpmnTestEngine = new BpmnTestEngine(clock);
      bpmnTestEngine.init();
    }
    bpmnTestEngine.clear();
  }

  @AfterAll
  static void closeEngine() {
    if (bpmnTestEngine != null) {
      bpmnTestEngine.close();
    }
  }

  @Test
  void testParallelGateway()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
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
  void testInclusiveGateway_DefaultFlow()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(1)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_3")
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(2)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(3)))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep3()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
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
  void testExclusiveGatewy()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
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

  private class DummyObject {

    private final int i;

    public DummyObject(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }
  }
}
