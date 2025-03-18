package com.flomaestro.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;

import com.flomaestro.engine.pi.testengine.SingletonBpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessInstanceProcessorTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testDeploy() throws IOException {
    ProcessDefinitionDTO processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();

    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(1);

    processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single-2.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single-2.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);
  }

  @Test
  void testProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
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
  void testSubProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessServiceTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
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
  void testSubProcessTaskNested() throws IOException {
    SingletonBpmnTestEngine.getInstance()
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
  void testProcessCallActivitySingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(
            VariablesDTO.of("startVariable", "valueStart", "calledActivity", "calledActivity"))
        .waitUntilChildProcessIsCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("calledActivity:StartEvent_CalledElement")
        .hasInstantiatedElementWithId("calledActivity:task_CalledElement")
        .hasInstantiatedElementWithId("calledActivity:EndEvent_CalledElement")
        .toProcessLevel()
        .assertThatParentProcess()
        .hasInstantiatedElementWithId("task-callactivity:StartEvent_1")
        .toProcessLevel()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("callactivity-id")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  @Disabled
  void testScheduledStart_R5() throws IOException {

    SingletonBpmnTestEngine.getInstance()
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

  @Test
  @Disabled
  void testTerminateSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsActive("SubTask_1")
        .terminateProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  void testTerminateChildSingleCallActivityInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-callactivity.bpmn")
        .startProcessInstance(VariablesDTO.empty())
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
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  @Disabled
  void testTerminateSpecificSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsWaiting("SubTask_1")
        .terminateElementInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  void testMessageStartEvent() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/message_start.bpmn")
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
  void testReceiveTask() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/receive-task.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("ReceiveTaskMessage", "Receive_Task_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted();
  }
}
