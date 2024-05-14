package nl.qunit.bpmnmeister.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
@TestMethodOrder(OrderAnnotation.class)
class ProcessInstanceProcessorTest {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessorTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
  }

  @Test
  void testDeploy()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    ProcessDefinition processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single.gen1.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(1);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single-2.gen1.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single-2.gen1.bpmn")
        .waitForProcessDeployment()
        .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values()).hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition = bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single.gen1.bpmn")
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
        .deployProcessDefinitionAndWait("/bpmn/task-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id")
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testSubProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    LOG.info("testSubProcessTaskSingle");
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilChildProcessIsStarted()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("SubStartEvent_1")
        .hasPassedElement("SubTask_1")
        .hasPassedElement("SubEndEvent_1")
        .toProcessLevel()
        .assertThatParentProcess()
        .hasPassedElement("StartEvent_1")
//        .hasNotPassedElement("EndEvent_1")
        .toProcessLevel()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("SubProcess_1")
        .hasPassedElement("EndEvent_1");
  }


  @Test
  void testProcessServiceTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasPassedElement("StartEvent_2")
        .hasPassedElement("service-task-id")
        .hasPassedElement("EndEvent_2");
  }


  @Test
  void testProcessServiceTaskSingleFx()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-fx.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasPassedElement("StartEvent_2")
        .hasPassedElement("service-task-id")
        .hasPassedElement("EndEvent_2");
  }

  @Test
  void testProcessServiceTaskFailed5Retries()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed1", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed2", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed3", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed4", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed5", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed5", "true"))
        .waitUntilCompleted()
        .assertThatProcess().hasFailed();
  }
  @Test
  void testProcessServiceTaskFailed3RetriesButThenSucceeds()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed1", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed2", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed3", "true"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess().isCompleted();
  }
  @Test
  void testProcessServiceTaskRetryBackoff()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-retry-backoff.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed1", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed2", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithFailure(true, "failure", Variables.of("failed3", "true"))
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("success", "true"))
        .waitUntilCompleted()
        .assertThatProcess()
        .isCompleted()
        .hasVariableMatching("failed1", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("failed2", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("failed3", val -> assertThat(val).isEqualTo("true"))
        .hasVariableMatching("success", val -> assertThat(val).isEqualTo("true"));
  }

  @Test
  void testProcessTaskMultiInstanceParallel()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-parallel.gen1.bpmn")
        .startProcessInstance(Variables.of("inputCollection", List.of("a", "b", "c")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching("outputCollection", val -> assertThat(val).asInstanceOf(LIST).containsExactlyInAnyOrder("axxx0", "bxxx1", "cxxx2"))
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id", 1)
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testProcessTaskMultiInstanceParallelMany()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-parallel.gen1.bpmn")
        .startProcessInstance(Variables.of("inputCollection", IntStream.range(0, 1000)
            .mapToObj(Integer::toString)
            .collect(Collectors.toList())))
        .waitUntilCompleted(Duration.ofSeconds(120))
        .assertThatProcess()
        .hasVariableMatching("outputCollection", val -> assertThat(val).asInstanceOf(LIST).hasSize(1000))
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id", 1)
        .hasPassedElement("EndEvent_1");
  }


  @Test
  void testProcessTaskMultiInstanceSequential()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-sequential.gen1.bpmn")
        .startProcessInstance(Variables.of("inputCollection", List.of("a", "b", "c", "d", "e", "f")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching("outputCollection", val -> assertThat(val).asInstanceOf(LIST).containsExactly("axxx0", "bxxx1", "cxxx2", "dxxx3", "exxx4", "fxxx5"))
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id", 1)
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testProcessSubTaskMultiInstanceSequential()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/subtask-multiinstance-sequential.gen1.bpmn")
        .startProcessInstance(Variables.of("inputCollection", List.of("a", "b", "c")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching("outputCollection", val -> assertThat(val).asInstanceOf(LIST).containsExactly("axxx0", "bxxx1", "cxxx2"))
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id", 1)
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testProcessCallActivitySingle()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilChildProcessIsStarted()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_CalledElement")
        .hasPassedElement("task_CalledElement")
        .hasPassedElement("EndEvent_CalledElement")
        .toProcessLevel()
        .assertThatParentProcess()
        .hasPassedElement("StartEvent_1")
        .toProcessLevel()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("callactivity-id")
        .hasPassedElement("EndEvent_1");

  }


  @Test
  void testProcessCallActivityMultiInstanceSequential()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-multiinstance-sequential.gen1.bpmn")
        .startProcessInstance(Variables.of("inputCollection", List.of("a", "b", "c")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching("outputCollection", oc -> assertThat(oc).isEqualTo(List.of("axxx0", "bxxx1", "cxxx2")))
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("callactivity-id", 1)
        .hasPassedElement("EndEvent_1");

  }

  @Test
  void testScheduledStart_R5()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r5.bpmn")
        .moveTimeForward(Duration.ofSeconds(3))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofSeconds(2))
        .waitForNewProcessInstance()
        .waitUntilCompleted();
  }

  @Test
  void testTerminateParentAndChildProcesses()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/terminate-child-processes.bpmn")
        .startProcessInstance(Variables.of("inputCollection", List.of("a", "b", "c", "d", "e", "f")))
        .waitUntilChildProcessesHaveState(6, ProcessInstanceState.ACTIVE)
        .parentProcess()
        .terminateProcessWithChildProcesses()
        .waitUntilCompleted()
        .assertThatProcess()
        .isTerminated()
        .toProcessLevel()
        .waitUntilChildProcessesHaveState(6, ProcessInstanceState.TERMINATED);
  }

  @Test
  void testBoundaryTimerTriggered()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(Variables.EMPTY)
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
        .startProcessInstance(Variables.EMPTY)
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
        .startProcessInstance(Variables.EMPTY)
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
  void testIntermediateTimerCatch()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/timer-intermediate-catch.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .setTime(Instant.parse("2024-02-29T07:59:59Z"))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofMillis(1001))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("CatchEvent_1")
        .hasPassedElement("Task_1")
        .hasPassedElement("EndEvent_1");
  }
  @Test
  void testExclusiveGatewy()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sequence-flow-condition.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("EndEvent_1")

        // now test the alternative default flow
        .toProcessLevel()
        .startProcessInstance(Variables.EMPTY)
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_1")
        .hasPassedElement("EndEvent_1");


  }
}