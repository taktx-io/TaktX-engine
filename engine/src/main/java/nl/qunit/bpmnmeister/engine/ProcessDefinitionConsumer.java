package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBException;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
@RequiredArgsConstructor
public class ProcessDefinitionConsumer {
  private static final Logger LOG = Logger.getLogger(ProcessDefinitionConsumer.class);
  final ProcessDefinitionService processDefinitionService;

  @Incoming("process-definition-xml-incoming")
  @Transactional
  public void consume(String xml) throws JAXBException {
    LOG.info("Received process timeCycle xml: " + xml);
    processDefinitionService.persistProcessDefinition(xml);
  }
}
