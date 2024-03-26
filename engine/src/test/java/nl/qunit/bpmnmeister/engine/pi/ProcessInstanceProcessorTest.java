package nl.qunit.bpmnmeister.engine.pi;

import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

@QuarkusContainerKafkaTest
class ProcessInstanceProcessorTest  {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessorTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;

  @Test
  void testProcessTaskSingle()
      throws InterruptedException, ExecutionException, IOException, JAXBException, NoSuchAlgorithmException {
    String xml = IOUtils.toString(Objects.requireNonNull(
        ProcessInstanceProcessorTest.class.getResourceAsStream(
            "/bpmn/task-single.gen1.bpmn")));
    Definitions parse = BpmnParser.parse(xml, 1);
    ProcessDefinition processDefinition = new ProcessDefinition(parse, 1);
    ProcessInstanceKey processInstanceKey = bpmnTestEngine.triggerNewProcessInstance(processDefinition,
        new BaseElementId("StartEvent_1"));

    ProcessInstance processInstance = bpmnTestEngine.waitUntilCompleted(processInstanceKey);
    BpmnAssert.assertThat(processInstance).isCompleted()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("service-task-id")
        .hasPassedElement("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskSingle()
      throws InterruptedException, ExecutionException, IOException, JAXBException, NoSuchAlgorithmException {
    String xml = IOUtils.toString(Objects.requireNonNull(
        ProcessInstanceProcessorTest.class.getResourceAsStream(
            "/bpmn/servicetask-single.gen1.bpmn")));
    Definitions parse = BpmnParser.parse(xml, 1);
    ProcessDefinition processDefinition = new ProcessDefinition(parse, 1);

    ProcessInstanceKey processInstanceKey = bpmnTestEngine.triggerNewProcessInstance(processDefinition,
        new BaseElementId("StartEvent_1"));

    ExternalTaskTrigger externalTaskTrigger = bpmnTestEngine.waitUntilServiceTaskIsWaitingForResponse(
        processInstanceKey, "service-task-id");





  }

}