package nl.qunit.bpmnmeister.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;

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
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ExternalTaskTest {

  private static final Logger LOG = Logger.getLogger(ExternalTaskTest.class);

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
  void testProcessServiceTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("ServiceTask_1")
        .hasPassedElementWithId("EndEvent_1");
  }


  @Test
  void testProcessServiceTaskSingleFx()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-fx.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("ServiceTask_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskFailed5Retries()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail", "failure", VariablesDTO.of("failed1", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed2", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed3", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed4", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed5", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed5", "true"))
        .waitUntilElementHasFailed("ServiceTask_1")
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("ServiceTask_1")
        .doesNotHaveVariable("MappedOutputVariable")
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .isStillActive();
  }

  @Test
  void testProcessServiceTaskFailed3RetriesButThenSucceeds()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed1", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed2", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed3", "true"))
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondWithSuccess(VariablesDTO.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess().isCompleted();
  }

  @Test
  void testProcessServiceTaskRetryBackoff()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-retry-backoff.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed1", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed2", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilExternalTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "fail","failure", VariablesDTO.of("failed3", "true"))
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
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sendtask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("send-task-id")
        .andRespondWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasPassedElementWithId("StartEvent_2")
        .hasPassedElementWithId("send-task-id")
        .hasPassedElementWithId("EndEvent_2");
  }

}