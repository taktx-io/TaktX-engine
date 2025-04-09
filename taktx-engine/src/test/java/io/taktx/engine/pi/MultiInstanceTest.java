package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MultiInstanceTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessTaskMultiInstanceParallel() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-parallel.bpmn")
        .startProcessInstance(VariablesDTO.of("inputCollection", List.of("a", "b", "c")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching(
            "outputCollection",
            val ->
                assertThat(val)
                    .asInstanceOf(LIST)
                    .containsExactlyInAnyOrder("axxx0", "bxxx1", "cxxx2"))
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("task-id/task-id", TaskInstanceDTO.class, 3)
        .hasInstantiatedElementWithId("task-id", MultiInstanceInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessTaskMultiInstanceParallelMany() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-parallel.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "inputCollection", IntStream.range(0, 1000).mapToObj(Integer::toString).toList()))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching(
            "outputCollection", val -> assertThat(val).asInstanceOf(LIST).hasSize(1000))
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("task-id/task-id", TaskInstanceDTO.class, 1000)
        .hasInstantiatedElementWithId("task-id", MultiInstanceInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessTaskMultiInstanceSequentialMany() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-sequential.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "inputCollection", IntStream.range(0, 1000).mapToObj(Integer::toString).toList()))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching(
            "outputCollection", val -> assertThat(val).asInstanceOf(LIST).hasSize(1000))
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("task-id/task-id", TaskInstanceDTO.class, 1000)
        .hasInstantiatedElementWithId("task-id", MultiInstanceInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessSubTaskMultiInstanceSequential() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/subtask-multiinstance-sequential.bpmn")
        .startProcessInstance(VariablesDTO.of("inputCollection", List.of("a", "b", "c")))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableMatching(
            "outputCollection",
            val -> assertThat(val).asInstanceOf(LIST).containsExactly("axxx0", "bxxx1", "cxxx2"))
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("task-id/task-id", SubProcessInstanceDTO.class, 3)
        .hasInstantiatedElementWithId("task-id", MultiInstanceInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessCallActivityMultiInstanceSequential() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-multiinstance-sequential.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "inputCollection", List.of("a", "b", "c"), "calledActivity", "calledActivity"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasCollectioneMatching(
            "outputCollection", oc -> assertThat(oc).containsExactly("axxx0", "bxxx1", "cxxx2"))
        .hasInstantiatedElementWithId("task-callactivity-multiinstance-sequential:StartEvent_1")
        .hasInstantiatedElementWithId(
            "task-callactivity-multiinstance-sequential:callactivity-id/callactivity-id",
            CallActivityInstanceDTO.class,
            3)
        .hasInstantiatedElementWithId(
            "task-callactivity-multiinstance-sequential:callactivity-id",
            MultiInstanceInstanceDTO.class,
            1)
        .hasInstantiatedElementWithId("task-callactivity-multiinstance-sequential:EndEvent_1");
  }

  @Test
  void testProcessCallActivityMultiInstanceParallel() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-multiinstance-parallel.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "inputCollection", List.of("a", "b", "c"), "calledActivity", "calledActivity"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasCollectioneMatching(
            "outputCollection",
            oc -> assertThat(oc).containsExactlyInAnyOrder("axxx0", "bxxx1", "cxxx2"))
        .hasInstantiatedElementWithId("task-callactivity-multiinstance-parallel:StartEvent_1")
        .hasInstantiatedElementWithId(
            "task-callactivity-multiinstance-parallel:callactivity-id/callactivity-id",
            CallActivityInstanceDTO.class,
            3)
        .hasInstantiatedElementWithId(
            "task-callactivity-multiinstance-parallel:callactivity-id",
            MultiInstanceInstanceDTO.class,
            1)
        .hasInstantiatedElementWithId("task-callactivity-multiinstance-parallel:EndEvent_1");
  }

  @Test
  void testProcessTaskMultiInstanceExpressionParallel() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-expression-parallel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted();
  }

  @Test
  void testReceiveTask_multiInstance() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/receive-task-multiinstance.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(VariablesDTO.empty())
        .waitForMessageSubscription(
            "ReceiveTaskMessage", "Receive_Task_1", Set.of("1", "2", "3", "4", "5"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "5", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "3", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "2", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "4", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted();
  }
}
