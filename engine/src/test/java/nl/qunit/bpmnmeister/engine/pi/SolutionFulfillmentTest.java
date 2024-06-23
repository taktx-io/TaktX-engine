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
class SolutionFulfillmentTest {

  private static final Logger LOG = Logger.getLogger(SolutionFulfillmentTest.class);

  @Inject
  BpmnTestEngine bpmnTestEngine;


  @PostConstruct
  void init() {
    LOG.info("Init BpmnTestEngine: " + bpmnTestEngine);
    bpmnTestEngine.clear();
  }


  @Test
  void testSolutionFulfillment_timeoutSafetyDossierObtained()
      throws IOException, JAXBException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {

    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/SolutionFulfillment.bpmn")
        .startProcessInstance(Variables.empty())
        .waitUntilServiceTaskIsWaitingForResponse("DetermineTimeoutTask")
        .andRespondWithSuccess(Variables.of("waitForSafetyDossierObtained", "PT5M", "waitForSafetyDossierEnd", "PT10M"))
        .waitUntilChildProcessIsStarted()
        .waitUntilElementIsActive("SafetyDossierObtainedTimeoutBoundaryEvent")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .waitUntilServiceTaskIsWaitingForResponse("SendTimeoutOccurredExceptionSubProcessTask")
        .andRespondWithSuccess(Variables.empty())
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .parentProcess()
        .waitUntilServiceTaskIsWaitingForResponse("SendTimeoutOccurredExceptionTask")
        .andRespondWithSuccess(Variables.empty())
        .waitUntilCompleted();
  }


}