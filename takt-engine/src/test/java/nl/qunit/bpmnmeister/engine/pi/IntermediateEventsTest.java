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
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;
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
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/timer-intermediate-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .setTime(Instant.parse("2024-02-29T07:59:59Z"))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofMillis(1001))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("CatchEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testMessageIntermediateCatch()
      throws JAXBException,
      NoSuchAlgorithmException,
      IOException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/message-intermediate-catch.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription(
            "IntermediateCatchMessage", "MessageIntermediateCatchEvent_1", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "IntermediateCatchMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("MessageIntermediateCatchEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testLinkIntermediateThrowCatch()
      throws IOException,
      JAXBException,
      NoSuchAlgorithmException,
      ParserConfigurationException,
      SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/link-intermediate-catch-throw.bpmn")
        .startProcessInstance(VariablesDTO.of("input", "value"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Throw_1")
        .hasInstantiatedElementWithId("Catch_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Catch_2")
        .hasVariableWithValue("input", "value")
        .hasVariableWithValue("linkOutput_1", 123.0)
        .hasVariableWithValue("linkOutput_2", 456.0);
  }
}
