package nl.qunit.bpmnmeister.engine.pi;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
@TestMethodOrder(OrderAnnotation.class)
class BoundaryEventsTest {

  private static final Logger LOG = Logger.getLogger(BoundaryEventsTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
  }


  @Test
  void testBoundaryTimerTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess().isCompleted()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Boundary_Timer_1")
        .hasPassedElement("EndEvent_2")
        .hasNotPassedElement("EndEvent_1");
  }

  @Test
  void testBoundaryTimerNotTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .assertThatProcess().isCompleted()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("EndEvent_1")
        .hasNotPassedElement("Boundary_Timer_1")
        .hasNotPassedElement("EndEvent_2");
  }

  @Test
  void testBoundaryTimerNonInterrupting()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-non-interrupting.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .andRespondWithSuccess(Variables.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("EndEvent_1")
        .hasPassedElement("Boundary_Timer_1", 3)
        .hasPassedElement("Interrupted_Task_1", 3);
  }



  @Test
  void testBoundaryMessageInterrupting()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitForMessageSubscription("BoundaryMessage", "BoundaryEvent_1",
            Set.of("key1"))
        .andSendMessageWithCorrelationKey("BoundaryMessage", "key1",
            Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("BoundaryEvent_1")
        .hasPassedElement("EndEvent_2")
        .hasNotPassedElement("EndEvent_1")
        .hasNotPassedElement("service-task-id")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testBoundaryMessageNonInterrupting()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message-non-interrupting.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .waitForMessageSubscription("BoundaryEventMessage", "BoundaryEvent_1",
            Set.of("key1"))
        .andSendMessageWithCorrelationKey("BoundaryEventMessage", "key1",
            Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("BoundaryEventMessage", "key1",
            Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("BoundaryEventMessage", "key1",
            Variables.of("var1", "value1"))
        .waitUntilElementHasPassed("BoundaryEvent_1", 3)
        .andRespondWithSuccess(Variables.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("service-task-id")
        .hasPassedElement("BoundaryEvent_1", 3)
        .hasPassedElement("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("var2", "value2");

  }

}