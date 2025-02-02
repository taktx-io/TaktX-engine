package com.flomaestro.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;

import com.flomaestro.engine.pi.testengine.BpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ExternalTaskTest {

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
  void testProcessServiceTaskSingle()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskSingleFx()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-fx.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskFailed5Retries()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed1", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed2", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed3", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed4", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed5", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed5", "true"))
        .waitUntilElementHasTerminated("ServiceTask_1")
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("ServiceTask_1")
        .doesNotHaveVariable("MappedOutputVariable")
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .isCompleted();
  }

  @Test
  void testProcessServiceTaskFailed3RetriesButThenSucceeds()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed1", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed2", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed3", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .isCompleted();
  }

  @Test
  void testProcessServiceTaskRetryBackoff()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-retry-backoff.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed1", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed2", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail", "123", "failure", VariablesDTO.of("failed3", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .isCompleted()
        .hasVariableMatching("failed1", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("failed2", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("failed3", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("success", val -> assertThat(val).isEqualTo("true"));
  }

  @Test
  void testSendTask_Single()
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sendtask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("send-task-id")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasInstantiatedElementWithId("StartEvent_2")
        .hasInstantiatedElementWithId("send-task-id")
        .hasInstantiatedElementWithId("EndEvent_2");
  }

  @Test
  void testProcessServiceTaskTimeout()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .moveTimeForward(Duration.ofDays(7).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskPromise()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithPromise("P10D")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofDays(7).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofDays(3))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasTerminatedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }
}
