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
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ErrorsTest {

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
  void testInterruptingErrorTriggered()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(
            false, "Error_1tlo99v", "456", "message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_WithErrorReference")
        .hasInstantiatedElementWithId("EndEvent_Error_WithErrorReference")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value1");
  }

  @Test
  void testInterruptingError_CatchAllTriggered()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(
            false, "Error_1tlo99v", "478", "message", VariablesDTO.of("var1", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value2");
  }

  @Test
  void testInterruptingError_NoCode_CatchAllTriggered()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(false, null, null, null, VariablesDTO.of("var1", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value2");
  }

  @Test
  void testInterruptingErrorTriggeredInSubprocess()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithFailure(
            false, "Error_1tlo99v", "456", "error message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_Reference")
        .hasInstantiatedElementWithId("EndEvent_Reference");
  }

  @Test
  void testCatchAllErrorTriggeredInSubprocess()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithFailure(
            false, "Error_1tlo99v", "789", "error message", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference");
  }

  @Test
  void testNoErrorTriggeredInSubprocess()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("Subprocess_1/SubStartEvent")
        .hasInstantiatedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasInstantiatedElementWithId("Subprocess_1/SubEndEvent")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference")
        .hasVariableWithValue("var1", "value1");
  }
}
