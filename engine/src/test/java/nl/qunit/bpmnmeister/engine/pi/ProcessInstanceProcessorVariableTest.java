package nl.qunit.bpmnmeister.engine.pi;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
@TestMethodOrder(OrderAnnotation.class)
class ProcessInstanceProcessorVariableTest {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessorVariableTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
  }

  @Test
  void testOutputStartevent()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.gen1.bpmn")
        .startProcessInstance(Variables.of("var1", "value1"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .assertThatExternalTask()
        .doesNotHaveVariable("StartEvent_Output_1")
        .doesNotHaveVariable("StartEvent_Output_2")
        .hasVariableWithValue("inputVariable", "123.0")
        .toProcessLevel()
        .andRespondWithSuccess(Variables.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("var2", "value2")
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .hasVariableWithValue("MappedOutputVariable", "value1");
  }

}