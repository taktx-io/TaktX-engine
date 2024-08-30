package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ProcessInstanceProcessorVariableTest {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessorVariableTest.class);

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
  void testOutputStartevent()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.gen1.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilServiceTaskIsWaitingForResponse("service-task-id")
        .assertThatExternalTask()
        .doesNotHaveVariable("StartEvent_Output_1")
        .doesNotHaveVariable("StartEvent_Output_2")
        .hasVariableWithValue("inputVariable", "123.0")
        .toProcessLevel()
        .andRespondWithSuccess(VariablesDTO.of("var2", "value2"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasVariableWithValue("var2", "value2")
        .hasVariableWithValue("StartEvent_Output_1", "outputValue1")
        .hasVariableWithValue("StartEvent_Output_2", "outputValue2")
        .hasVariableWithValue("MappedOutputVariable", "value1");
  }

}