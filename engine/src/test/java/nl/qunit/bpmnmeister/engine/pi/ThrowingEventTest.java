package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class ThrowingEventTest {

  private static final Logger LOG = Logger.getLogger(ThrowingEventTest.class);

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
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Timer_1")
        .hasPassedElementWithId("Task_2")
        .hasPassedElementWithId("EndEvent_2")
        .hasTerminatedElement("Task_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .isTerminated();
  }


}