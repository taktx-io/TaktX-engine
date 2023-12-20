package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBException;
import java.util.Optional;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;

@ApplicationScoped
public class ProcessDefinitionService {
  private static final String QUERY = "processDefinitionId = :pdid and hash = :hash";

  @Inject ProcessDefinitionRepository processDefinitionRepository;
  @Inject ProcessDefinitionXmlRepository processDefinitionXmlRepository;
  @Inject BpmnParser bpmnParser;

  @Transactional
  public void persistProcessDefinition(String xml) throws JAXBException {
    int xmlHash = xml.hashCode();
    ProcessDefinition parsedProcessDefinition = bpmnParser.parse(xml);

    String processDefinitionId = parsedProcessDefinition.processDefinitionId;
    Optional<ProcessDefinitionXml> optXmlEntity =
        processDefinitionXmlRepository
            .find(QUERY, Parameters.with("pdid", processDefinitionId).and("hash", xmlHash))
            .firstResultOptional();
    if (optXmlEntity.isEmpty()) {
      // Non-existing process definition, persist it
      ProcessDefinitionXml xmlEntity =
          ProcessDefinitionXml.builder()
              .processDefinitionId(processDefinitionId)
              .xml(xml)
              .hash(xmlHash)
              .build();
      processDefinitionXmlRepository.persist(xmlEntity);
      long newVersion = processDefinitionXmlRepository.count();
      ProcessDefinition newProcessDefinitionToPersist =
          ProcessDefinition.builder()
              .xmlObjectId(xmlEntity.id)
              .processDefinitionId(processDefinitionId)
              .version(newVersion)
              .bpmnElements(parsedProcessDefinition.bpmnElements)
              .flows(parsedProcessDefinition.flows)
              .build();
      processDefinitionRepository.persist(newProcessDefinitionToPersist);
    }
  }
}
