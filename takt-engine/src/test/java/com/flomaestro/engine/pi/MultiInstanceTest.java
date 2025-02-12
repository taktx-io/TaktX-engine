package com.flomaestro.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.flomaestro.engine.pi.testengine.BpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.MultiInstanceInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.TaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class MultiInstanceTest {

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
  void testProcessTaskMultiInstanceParallel()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessTaskMultiInstanceParallelMany()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessTaskMultiInstanceSequentialMany()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessSubTaskMultiInstanceSequential()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessCallActivityMultiInstanceSequential()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessCallActivityMultiInstanceParallel()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
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
  void testProcessTaskMultiInstanceExpressionParallel()
      throws IOException,
          JAXBException,
          NoSuchAlgorithmException,
          ParserConfigurationException,
          SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/task-multiinstance-expression-parallel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted();
  }

  @Test
  void testReceiveTask_multiInstance()
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    bpmnTestEngine
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
