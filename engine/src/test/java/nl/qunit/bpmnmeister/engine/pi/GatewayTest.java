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
        .hasNotPassedElement("Task_2")
        .hasNotPassedElement("EndEvent_1")
        .toProcessLevel()
        .moveTimeForward(Duration.ofHours(1).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testInclusiveGateway_DefaultFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_3")
        .hasNotPassedElement("Task_1")
        .hasNotPassedElement("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasNotPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("Task_3");
  }


  @Test
  void testInclusiveGateway_MultipleFlows_Deep3()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable2", "a", "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("Task_3");
  }



  @Test
  void testExclusiveGatewy()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sequence-flow-condition.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("EndEvent_1")

        // now test the alternative default flow
        .toProcessLevel()
        .startProcessInstance(Variables.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_1")
        .hasPassedElement("EndEvent_1");
  }

}