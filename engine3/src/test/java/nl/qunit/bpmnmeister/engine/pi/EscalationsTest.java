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
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class EscalationsTest {

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
  void testInterruptingEscalationTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation("Escalation_02db004", "interrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value_interrupting");
  }

  @Test
  void testNonInterruptingEscalationTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithEscalation("Escalation_16vkrj5", "noninterrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation("Escalation_16vkrj5", "noninterrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_NonInterrupting", 2)
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value_noninterrupting")
        .hasVariableWithValue("MappedOutputVariable2", "value_normal");
  }


  @Test
  void testInterruptingEscalationTriggeredInSubprocess()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithEscalation("Escalation_02db004", "interrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasNotPassedElementWithId("EndEvent_Noninterrupting");
//        .hasTerminatedElementWithId("ServiceTask_1")
//        .hasVariableWithValue("MappedOutputVariable", "value_interrupting");
  }

  @Test
  void testNonInterruptingEscalationTriggeredInSubprocess()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondWithEscalation("Escalation_16vkrj5", "noninterrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation("Escalation_16vkrj5", "noninterrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondWithEscalation("Escalation_02db004", "interrupting", "escalation message",
            VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasInstantiatedElementWithId("EndEvent_Noninterrupting", 2)
        .hasPassedElementWithId("EscalationBoundaryEvent_Noninterrupting", 2);
//        .hasTerminatedElementWithId("ServiceTask_1")
//        .hasVariableWithValue("MappedOutputVariable", "value_interrupting");
  }

}