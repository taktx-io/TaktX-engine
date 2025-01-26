package com.flomaestro.engine.pi;

import com.flomaestro.engine.pi.testengine.BpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class EscalationsTest {

  @Inject Clock clock;

  static BpmnTestEngine bpmnTestEngine;

  @PostConstruct
  void init() {
    if (bpmnTestEngine == null) {
      bpmnTestEngine = new BpmnTestEngine(clock);
      bpmnTestEngine.init();
    }
    bpmnTestEngine.reset();
  }

  @AfterAll
  static void closeEngine() {
    if (bpmnTestEngine != null) {
      bpmnTestEngine.close();
    }
  }

  @Test
  void testInterruptingEscalationTriggered()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation(
            "Escalation_02db004",
            "interrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value_interrupting");
  }

  @Test
  void testInterruptingEscalation_CatchAllTriggered()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation(
            "Escalation_02db004",
            "non-matching",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "CatchAll");
  }

  @Test
  void testInterruptingEscalation_NoCode_CatchAllTriggered()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation(null, null, null, VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "CatchAll");
  }

  @Test
  void testNonInterruptingEscalationTriggered()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation(
            "Escalation_16vkrj5",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation(
            "Escalation_16vkrj5",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_NonInterrupting", 2)
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value_noninterrupting")
        .hasVariableWithValue("MappedOutputVariable2", "value_normal");
  }

  @Test
  void testInterruptingEscalationTriggeredInSubprocess()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithEscalation(
            "Escalation_02db004",
            "interrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasNotPassedElementWithId("EndEvent_Noninterrupting")
        .hasTerminatedElementWithId("Subprocess_1/SubServiceTask_1");
  }

  @Test
  void testNonInterruptingEscalationTriggeredInSubprocess()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithEscalation(
            "Escalation_16vkrj5",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation(
            "Escalation_16vkrj5",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation(
            "Escalation_02db004",
            "interrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasInstantiatedElementWithId("EndEvent_Noninterrupting", 2)
        .hasPassedElementWithId("EscalationBoundaryEvent_Noninterrupting", 2);
  }

  @Test
  void testNoEscalationTriggeredInSubprocess()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting_1")
        .hasNotPassedElementWithId("EndEvent_Noninterrupting")
        .hasNotPassedElementWithId("EscalationBoundaryEvent_Noninterrupting")
        .hasVariableWithValue("var1", "value1");
  }
}
