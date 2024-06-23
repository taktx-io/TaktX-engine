package nl.qunit.bpmnmeister.engine.pi;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.engine.pi.testengine.BpmnTestEngine;
import nl.qunit.bpmnmeister.engine.pi.testengine.QuarkusContainerKafkaTest;
import nl.qunit.bpmnmeister.pi.Variables;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusContainerKafkaTest
class IntermediateEventsTest {

  private static final Logger LOG = Logger.getLogger(IntermediateEventsTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
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
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("CatchEvent_1")
        .hasPassedElement("Task_1")
        .hasPassedElement("EndEvent_1");
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
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("MessageIntermediateCatchEvent_1")
        .hasPassedElement("EndEvent_1")
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
        .hasPassedElement("StartEvent_1")
        .hasPassedElement("Throw_1")
        .hasPassedElement("Catch_1")
        .hasPassedElement("EndEvent_1")
        .hasNotPassedElement("Catch_2")
        .hasVariableWithValue("input", "value")
        .hasVariableWithValue("linkOutput_1", 123.0)
        .hasVariableWithValue("linkOutput_2", 456.0);
  }

}