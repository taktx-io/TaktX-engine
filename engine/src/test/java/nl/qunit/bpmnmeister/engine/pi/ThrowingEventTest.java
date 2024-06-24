package nl.qunit.bpmnmeister.engine.pi;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
class ThrowingEventTest {

  private static final Logger LOG = Logger.getLogger(ThrowingEventTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
  }


  @Test
  void testTerminateEnd()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/terminate-end-event.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("Task_1")
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Timer_1")
        .hasPassedElement("Task_2")
        .hasPassedElement("EndEvent_2")
        .hasTerminatedElement("Task_1")
        .hasNotPassedElement("EndEvent_1")
        .isTerminated();
  }


}