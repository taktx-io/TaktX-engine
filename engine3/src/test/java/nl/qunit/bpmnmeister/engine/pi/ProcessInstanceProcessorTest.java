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
import java.util.List;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Task_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test @Disabled
  void testSubProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.gen1.bpmn")
        .startProcessInstance(VariablesDTO.empty())
//        .waitUntilChildProcessIsStarted()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("SubProcess_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test @Disabled
  void testSubProcessTaskNested()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-nested.gen1.bpmn")
        .startProcessInstance(VariablesDTO.empty())
//        .waitUntilChildProcessIsStarted()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("SubProcess_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test @Disabled
  void testProcessCallActivitySingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.gen1.bpmn")
        .startProcessInstance(VariablesDTO.empty())
//        .waitUntilChildProcessIsStarted()
        .waitUntilCompleted()
        .assertThatProcess()
//        .hasPassedElement("StartEvent_CalledElement")
//        .hasPassedElement("task_CalledElement")
//        .hasPassedElement("EndEvent_CalledElement")
//        .toProcessLevel()
//        .assertThatParentProcess()
        .hasPassedElementWithId("StartEvent_1")
//        .toProcessLevel()
//        .waitUntilCompleted()
//        .assertThatProcess()
        .hasPassedElementWithId("callactivity-id")
        .hasPassedElementWithId("EndEvent_1");

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
        .hasPassedElementWithId("Task", 1);
  }

  @Test @Disabled
  void testTerminateParentAndChildProcesses()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/terminate-child-processes.bpmn")
        .startProcessInstance(
            VariablesDTO.of("inputCollection", List.of("a", "b", "c", "d", "e", "f")))
        .waitUntilChildProcessesHaveState(6, ProcessInstanceState.ACTIVE)
        .parentProcess()
        .terminateProcessWithChildProcesses()
        .waitUntilCompleted()
        .assertThatProcess()
        .isTerminated()
        .toProcessLevel()
        .waitUntilChildProcessesHaveState(6, ProcessInstanceState.TERMINATED);
  }

  @Test @Disabled
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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .toProcessLevel()
        .sendMessage("StartMessage2", VariablesDTO.of("var2", "value2"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasVariableWithValue("var2", "value2")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/message_start_2.bpmn")
        .waitForMessageSubscription("StartMessage3")
        .sendMessage("StartMessage3", VariablesDTO.of("var3", "value3"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_2")
        .hasPassedElementWithId("EndEvent_2")
        .hasVariableWithValue("var3", "value3")
        .toProcessLevel();
  }

  @Test @Disabled
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