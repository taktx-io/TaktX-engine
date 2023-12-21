package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBException;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessDefinitionConsumer {
  private static final Logger LOG = Logger.getLogger(ProcessDefinitionConsumer.class);
  @Inject ProcessDefinitionService processDefinitionService;

  @Incoming("process-definition-xml-incoming")
  @Transactional
  public void consume(String xml) throws JAXBException {
    LOG.info("Received process definition xml: " + xml);
    processDefinitionService.persistProcessDefinition(xml);
  }
}
