package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;

@ApplicationScoped
public class ProcessDefinitionService {
  private static final String QUERY_HASH = "processDefinitionId = :pdid and hash = :hash";
  private static final String QUERY_PROCESSDEFINITION_NOVERSION = "processDefinitionId = :pdid";
  private static final String QUERY_PROCESSDEFINITION_VERSION =
      "processDefinitionId = :pdid and version = :version";

  @Inject ProcessDefinitionRepository processDefinitionRepository;
  @Inject ProcessDefinitionXmlRepository processDefinitionXmlRepository;
  @Inject BpmnParser bpmnParser;

  public ProcessDefinition persistProcessDefinition(String xml) throws JAXBException {
    int xmlHash = xml.hashCode();
    ProcessDefinition parsedProcessDefinition = bpmnParser.parse(xml);

    String processDefinitionId = parsedProcessDefinition.processDefinitionId;
    Optional<ProcessDefinitionXml> optXmlEntity =
        processDefinitionXmlRepository
            .find(QUERY_HASH, Parameters.with("pdid", processDefinitionId).and("hash", xmlHash))
            .firstResultOptional();
    if (optXmlEntity.isEmpty()) {
      // Non-existing process definition, persist it
      ProcessDefinitionXml xmlEntity =
          new ProcessDefinitionXml(null, processDefinitionId, xml, xmlHash);
      processDefinitionXmlRepository.persist(xmlEntity);
      long newVersion = processDefinitionXmlRepository.count();
      ProcessDefinition newProcessDefinitionToPersist =
          new ProcessDefinition(
              null,
              xmlEntity.id,
              processDefinitionId,
              newVersion,
              parsedProcessDefinition.bpmnElements,
              parsedProcessDefinition.flows);
      processDefinitionRepository.persist(newProcessDefinitionToPersist);
      return newProcessDefinitionToPersist;
    } else {
      throw new WebApplicationException("Resource already exists", Response.Status.CONFLICT);
    }
  }

  public ProcessDefinition getProcessDefinition(String processDefinitionId, long version) {
    Parameters queryparameters = Parameters.with("pdid", processDefinitionId);
    String query = QUERY_PROCESSDEFINITION_NOVERSION;
    if (version > 0) {
      queryparameters = queryparameters.and("version", version);
      query = QUERY_PROCESSDEFINITION_VERSION;
    }

    Optional<ProcessDefinition> processDefinition =
        processDefinitionRepository.find(query, queryparameters).stream()
            .max(Comparator.comparingLong(pd -> pd.version));
    return processDefinition.orElseThrow(
        () ->
            new NotFoundException(
                "Process definition not found: " + processDefinitionId + " version: " + version));
  }

  public List<ProcessDefinition> getProcessDefinitions() {
    return processDefinitionRepository.listAll();
  }
}
