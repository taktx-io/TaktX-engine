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
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class BoundaryEventsTest {

  @Inject Clock clock;

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
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimerNotTriggered()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_2");
  }

  @Test
  void testBoundaryTimerNonInterrupting()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-non-interrupting.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Interrupted_Task_1", 3)
        .hasInstantiatedElementWithId("Boundary_Timer_1", 1);
  }

  @Test
  void testBoundaryMessageInterrupting()
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("BoundaryMessage", "BoundaryEvent_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("BoundaryEvent_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("service-task-id")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testBoundaryMessageNonInterrupting()
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message-non-interrupting.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .waitForMessageSubscription("BoundaryEventMessage", "BoundaryEvent_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilElementHasPassed("BoundaryEvent_1", 1)
        .andRespondWithSuccess(VariablesDTO.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("service-task-id")
        .hasInstantiatedElementWithId("BoundaryEvent_1", 1)
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("var2", "value2");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_BoundaryEventEnd()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_2")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasTerminatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NormalEnd()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_2")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_NotTriggered_Subprocess_NormalEnd()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        //        .hasTerminatedElement("SubProcess_Boundary_Timer_1")
        //        .hasPassedElementWithId("SubStartEvent_1")
        //        .hasPassedElementWithId("Service_Task_1")
        //        .hasPassedElementWithId("SubEndEvent_1")
        .hasNotPassedElementWithId("Service_Task_2")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasInstantiatedElementWithId("OkTask")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NonInterrupting_BoundaryEventEnd_NoEnd()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess-noendevent.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_1")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilExternalTaskIsWaitingForResponse("Service_Task_2")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasTerminatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }
}
