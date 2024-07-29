package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class IntermediateEventsTest {

  private static final Logger LOG = Logger.getLogger(IntermediateEventsTest.class);

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
  void testIntermediateTimerCatch()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/timer-intermediate-catch.bpmn")
        .startProcessInstance(Variables.empty())
        .setTime(Instant.parse("2024-02-29T07:59:59Z"))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofMillis(1001))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("CatchEvent_1")
        .hasPassedElementWithId("Task_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test
  void testMessageIntermediateCatch()
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/message-intermediate-catch.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(Variables.of("correlationKey", "key1"))
        .waitForMessageSubscription("IntermediateCatchMessage", "MessageIntermediateCatchEvent_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey("IntermediateCatchMessage", "key1", Variables.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("MessageIntermediateCatchEvent_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1");


  }

  @Test
  void testLinkIntermediateThrowCatch()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/link-intermediate-catch-throw.bpmn")
        .startProcessInstance(Variables.of("input", "value"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Throw_1")
        .hasPassedElementWithId("Catch_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Catch_2")
        .hasVariableWithValue("input", "value")
        .hasVariableWithValue("linkOutput_1", 123.0)
        .hasVariableWithValue("linkOutput_2", 456.0);
  }

}