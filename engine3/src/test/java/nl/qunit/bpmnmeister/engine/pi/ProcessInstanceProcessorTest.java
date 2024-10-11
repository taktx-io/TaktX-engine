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
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ProcessInstanceProcessorTest {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessorTest.class);

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
  void testDeploy()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    ProcessDefinitionDTO processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(1);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single-2.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single-2.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(1);

  }

  @Test
  void testProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/task-single.bpmn")
        .startProcessInstance(VariablesDTO.of("key1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("key1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessServiceTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubTask_1")
        .andRespondWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessTaskNested()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-nested.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubTask_1")
        .andRespondWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessCallActivitySingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(VariablesDTO.of("startVariable", "valueStart"))
        .waitUntilChildProcessIsCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_CalledElement")
        .hasInstantiatedElementWithId("task_CalledElement")
        .hasInstantiatedElementWithId("EndEvent_CalledElement")
        .toProcessLevel()
        .assertThatParentProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .toProcessLevel()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("callactivity-id")
        .hasInstantiatedElementWithId("EndEvent_1");

  }


  @Test @Disabled
  void testScheduledStart_R5()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r5.bpmn")
        .moveTimeForward(Duration.ofSeconds(3))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofSeconds(2))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task", 1);
  }

  @Test @Disabled
  void testTerminateSingleElementInstances()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(
            VariablesDTO.empty())
        .waitUntilElementIsActive("SubTask_1")
        .terminateProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElements("SubProcess_1")
        .isTerminated();
  }

  @Test
  void testTerminateChildSingleCallActivityInstances()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-callactivity.bpmn")
        .startProcessInstance(
            VariablesDTO.empty())
        .waitUntilChildProcessIsStarted()
        .parentProcess()
        .terminateProcessInstance()
        .waitUntilChildProcessIsTerminated()
        .assertThatProcess()
        .isTerminated()
        .toProcessLevel()
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElements("SubProcess_1")
        .isTerminated();
  }

  @Test @Disabled
  void testTerminateSpecificSingleElementInstances()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(
            VariablesDTO.empty())
        .waitUntilElementIsWaiting("SubTask_1")
        .terminateElemeent()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElements("SubProcess_1")
        .isTerminated();
  }

  @Test 
  void testMessageStartEvent()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/message_start.bpmn")
        .waitForProcessDeployment()
        .waitForMessageSubscription("StartMessage2")
        .sendMessage("StartMessage2", VariablesDTO.of("var1", "value1"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .toProcessLevel()
        .sendMessage("StartMessage2", VariablesDTO.of("var2", "value2"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var2", "value2")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/message_start_2.bpmn")
        .waitForMessageSubscription("StartMessage3")
        .sendMessage("StartMessage3", VariablesDTO.of("var3", "value3"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_2")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasVariableWithValue("var3", "value3")
        .toProcessLevel();
  }

  @Test
  void testReceiveTask()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/receive-task.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("ReceiveTaskMessage", "Receive_Task_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted();
  }

}