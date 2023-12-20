package nl.qunit.bpmnmeister.engine;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessDefinitionConsumer {
  private static final Logger LOG = Logger.getLogger(ProcessDefinitionConsumer.class);
  @Inject BpmnParser bpmnParser;
  @Inject ProcessDefinitionXmlRepository processDefinitionXmlRepository;
  @Inject ProcessDefinitionRepository processDefinitionRepository;
  @Inject ProcessDefinitionService processDefinitionService;

  @Startup
  void init() throws IOException, JAXBException {
    Path filePath = Path.of("/Users/erichendriks/IdeaProjects/bpmnMeister/engine/loop.bpmn");
    String content = Files.readString(filePath);
    consume(content);
  }

  @Incoming("process-definition-xml-incoming")
  public void consume(String xml) throws JAXBException {
    LOG.info("Received process definition xml: " + xml);
    processDefinitionService.persistProcessDefinition(xml);
  }
}
