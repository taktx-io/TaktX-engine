package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.Variables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class BoundaryEventsTest {

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
  void testBoundaryTimerTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess().isCompleted()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Boundary_Timer_1")
        .hasPassedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1");
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_2");
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("Interrupted_Task_1", 3)
        .hasPassedElementWithId("Boundary_Timer_1", 1);
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("BoundaryEvent_1")
        .hasPassedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("service-task-id")
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
        .waitUntilElementHasPassed("BoundaryEvent_1", 1)
        .andRespondWithSuccess(Variables.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("service-task-id")
        .hasPassedElementWithId("BoundaryEvent_1", 1)
        .hasPassedElementWithId("EndEvent_1")
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
        .hasPassedElementWithId("StartEvent_1")
        .hasTerminatedElement("Subprocess_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Subprocess_1")
        .hasPassedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("OkTask");
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
        .hasPassedElementWithId("SubStartEvent_1")
        .hasPassedElementWithId("Service_Task_1")
        .hasPassedElementWithId("SubEndEvent_1")
        .hasNotPassedElementWithId("Service_Task_2")
        .toProcessLevel()
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Subprocess_1")
        .hasPassedElementWithId("EndEvent_2")
        .hasPassedElementWithId("OkTask")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1");
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
        .hasPassedElementWithId("StartEvent_1")
        .hasTerminatedElement("Subprocess_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }
}