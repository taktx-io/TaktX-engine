package com.flomaestro.engine.pi;

import com.flomaestro.engine.pi.testengine.BpmnTestEngine;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@QuarkusTest
class StraightThroughTest {

  @Inject Clock clock;

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
  void testStraighThrough()
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    bpmnTestEngine
        .deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ExternalTask_1")
        .andRespondWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .isTerminated()
        .hasTerminatedElementWithId("ExternalTask_1")
        .hasNotPassedElementWithId("Activity_0cxnpbx")
        .hasNotPassedElementWithId("Activity_1ohwsp7")
        .hasNotPassedElementWithId("Activity_09g9dzh")
        .hasNotPassedElementWithId("Event_1h5ln3k");
  }
}
