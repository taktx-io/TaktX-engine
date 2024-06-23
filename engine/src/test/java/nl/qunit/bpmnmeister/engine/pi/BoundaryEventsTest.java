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
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
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

  @Test
  void testBoundaryTimer_SubProcessTriggered_BoundaryEventEnd()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilChildProcessIsStarted()
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_2")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasTerminatedElement("Subprocess_1")
        .hasPassedElement("EndEvent_1")
        .hasPassedElement("Boundary_Timer_1")
        .hasNotPassedElement("OkTask");
  }


  @Test
  void testBoundaryTimer_SubProcessTriggered_NormalEnd()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilChildProcessIsStarted()
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_2")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Subprocess_1")
        .hasPassedElement("EndEvent_2")
        .hasNotPassedElement("Boundary_Timer_1")
        .hasNotPassedElement("EndEvent_1")
        .hasPassedElement("OkTask");
  }

  @Test
  void testBoundaryTimer_NotTriggered_Subprocess_NormalEnd()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilChildProcessIsStarted()
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_1")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElement("SubProcess_Boundary_Timer_1")
        .hasPassedElement("SubStartEvent_1")
        .hasPassedElement("Service_Task_1")
        .hasPassedElement("SubEndEvent_1")
        .hasNotPassedElement("Service_Task_2")
        .toProcessLevel()
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Subprocess_1")
        .hasPassedElement("EndEvent_2")
        .hasPassedElement("OkTask")
        .hasNotPassedElement("Boundary_Timer_1")
        .hasNotPassedElement("EndEvent_1");
  }


  @Test
  void testBoundaryTimer_SubProcessTriggered_NonInterrupting_BoundaryEventEnd_NoEnd()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess-noendevent.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilChildProcessIsStarted()
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilServiceTaskIsWaitingForResponse("Service_Task_2")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .parentProcess()
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasTerminatedElement("Subprocess_1")
        .hasPassedElement("EndEvent_1")
        .hasPassedElement("Boundary_Timer_1")
        .hasNotPassedElement("OkTask");
  }
}