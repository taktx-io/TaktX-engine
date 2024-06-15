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
import java.util.Set;
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .andRespondWithSuccess(Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasPassedElement("StartEvent_2")
        .hasPassedElement("service-task-id")
        .hasPassedElement("EndEvent_2");
  }


  @Test
  void testProcessServiceTaskSingleFx()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-fx.bpmn")
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task", 1);
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
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
        .startProcessInstance(Variables.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_1")
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testParallelGateway()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/gateway-parallel.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilElementHasPassed("Task_1")
        .assertThatProcess()
        .hasNotPassedElement("Task_2")
        .hasNotPassedElement("EndEvent_1")
        .toProcessLevel()
        .moveTimeForward(Duration.ofHours(1).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testInclusiveGateway_DefaultFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_3")
        .hasNotPassedElement("Task_1")
        .hasNotPassedElement("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasPassedElement("Task_2")
        .hasNotPassedElement("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasNotPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("Task_3");
  }


  @Test
  void testInclusiveGateway_MultipleFlows_Deep3()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(Variables.of("inputVariable", 2, "inputVariable2", "a", "inputVariable3", "a"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("Task_1")
        .hasNotPassedElement("Task_2")
        .hasPassedElement("Task_3");
  }

  @Test
  void testSendTask_Single()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/sendtask-single.gen1.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("send-task-id")
        .andRespondWithSuccess(Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasPassedElement("StartEvent_2")
        .hasPassedElement("send-task-id")
        .hasPassedElement("EndEvent_2");
  }

  @Test
  void testMessageStartEvent()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/message_start.bpmn")
        .waitForProcessDeployment()
        .waitForMessageSubscription("StartMessage2")
        .sendMessage("StartMessage2", Variables.of("var1", "value1"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .toProcessLevel()
        .sendMessage("StartMessage2", Variables.of("var2", "value2"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("EndEvent_1")
        .hasVariableWithValue("var2", "value2")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/message_start_2.bpmn")
        .waitForMessageSubscription("StartMessage3")
        .sendMessage("StartMessage3", Variables.of("var3", "value3"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_2")
        .hasPassedElement("EndEvent_2")
        .hasVariableWithValue("var3", "value3")
        .toProcessLevel();
  }

  @Test
  void testReceiveTask()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/receive-task.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitForMessageSubscription("ReceiveTaskMessage", "Receive_Task_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "key1", Variables.of("var1", "value1"))
        .waitUntilCompleted();
  }

  @Test
  void testReceiveTask_multiInstance()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/receive-task-multiinstance.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.empty())
        .waitForMessageSubscription("ReceiveTaskMessage", "Receive_Task_1", Set.of("1", "2", "3", "4", "5"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "5", Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "3", Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "1", Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "2", Variables.of("var1", "value1"))
        .andSendMessageWithCorrelationKey("ReceiveTaskMessage", "4", Variables.of("var1", "value1"))
        .waitUntilCompleted();
  }

  @Test
  void testMessageIntermediateCatch()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/message-intermediate-catch.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitForMessageSubscription("IntermediateCatchMessage", "MessageIntermediateCatchEvent_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey("IntermediateCatchMessage", "key1", Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("MessageIntermediateCatchEvent_1")
        .hasPassedElement("EndEvent_1")
        .hasVariableWithValue("var1", "value1");


  }

  @Test
  void testBoundaryMessageInterrupting()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitForMessageSubscription("BoundaryMessage", "BoundaryEvent_1",
            Set.of("key1"))
        .andSendMessageWithCorrelationKey("BoundaryMessage", "key1",
            Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("BoundaryEvent_1")
        .hasPassedElement("EndEvent_2")
        .hasNotPassedElement("EndEvent_1")
        .hasNotPassedElement("service-task-id")
        .hasVariableWithValue("var1", "value1");
  }
  @Test
  void testBoundaryMessageNonInterrupting()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/boundary-message-non-interrupting.bpmn")
        .waitForProcessDeployment()
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
        .waitUntilElementHasPassed("BoundaryEvent_1", 3)
        .andRespondWithSuccess(Variables.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("service-task-id")
        .hasPassedElement("BoundaryEvent_1", 3)
        .hasPassedElement("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("var2", "value2");

  }

}