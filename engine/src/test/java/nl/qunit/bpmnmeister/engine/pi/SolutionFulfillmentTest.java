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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest @Disabled
class SolutionFulfillmentTest {

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