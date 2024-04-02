package nl.qunit.bpmnmeister.engine.pi;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

  @Test @Order(2)
  void testProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException {
    bpmnTestEngine
        .deployProcessDefinition("/bpmn/task-single.gen1.bpmn")
        .startProcessInstance(Variables.EMPTY)
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("task-id")
        .hasPassedElement("EndEvent_1");
  }

  @Test @Order(3)
  void testSubProcessTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException {
    LOG.info("testSubProcessTaskSingle");
    bpmnTestEngine
        .deployProcessDefinition("/bpmn/subprocess-single.gen1.bpmn")
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
        .hasNotPassedElement("EndEvent_1")
        .toProcessLevel()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("SubProcess_1")
        .hasPassedElement("EndEvent_1");
  }


  @Test @Order(1)
  void testProcessServiceTaskSingle()
      throws IOException, JAXBException, NoSuchAlgorithmException {

    bpmnTestEngine
        .deployProcessDefinition("/bpmn/servicetask-single.gen1.bpmn")
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
}