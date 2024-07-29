package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class GatewayTest {

  private static final Logger LOG = Logger.getLogger(GatewayTest.class);

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
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/gateway-parallel.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilElementHasPassed("Task_1")
        .assertThatProcess()
        .hasNotPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .toProcessLevel()
        .moveTimeForward(Duration.ofHours(1).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Task_1")
        .hasPassedElementWithId("Task_2")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test
  void testInclusiveGateway_DefaultFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("Task_3")
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("Task_1")
        .hasPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("Task_1")
        .hasPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasPassedElementWithId("Task_3");
  }


  @Test
  void testInclusiveGateway_MultipleFlows_Deep3()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable2", "a", "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasPassedElementWithId("Task_3");
  }



  @Test
  void testExclusiveGatewy()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sequence-flow-condition.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasPassedElementWithId("EndEvent_1")

        // now test the alternative default flow
        .toProcessLevel()
        .startProcessInstance(Variables.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_1")
        .hasPassedElementWithId("EndEvent_1");
  }

}