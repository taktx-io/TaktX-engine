package nl.qunit.bpmnmeister.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.Variables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class MultiInstanceTest {

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

}