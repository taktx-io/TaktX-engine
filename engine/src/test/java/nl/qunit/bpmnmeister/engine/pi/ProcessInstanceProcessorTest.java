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
import org.junit.jupiter.api.Test;

@QuarkusContainerKafkaTest
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

  @Test
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