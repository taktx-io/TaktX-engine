package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBException;
import java.util.*;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;

@ApplicationScoped
public class ProcessDefinitionService {
  private static final String QUERY_PID = "processDefinitionId = :pdid";
  private static final String QUERY_PROCESSDEFINITION_NOVERSION = "processDefinitionId = :pdid";
  private static final String QUERY_PROCESSDEFINITION_VERSION =
      "processDefinitionId = :pdid and version = :version";

  @Inject ProcessDefinitionRepository processDefinitionRepository;
  @Inject ProcessDefinitionXmlRepository processDefinitionXmlRepository;
  @Inject BpmnParser bpmnParser;

  @Inject ProcessIntanceService processIntanceService;

  @Transactional
  public Definitions persistProcessDefinition(String xml) throws JAXBException {
    int xmlHash = xml.hashCode();
    Definitions parsedProcessDefinition = bpmnParser.parse(xml);

    String processDefinitionId = parsedProcessDefinition.getProcessDefinitionId();

    List<Integer> hashes =
        processDefinitionXmlRepository
            .find(QUERY_PID, Parameters.with("pdid", processDefinitionId))
            .stream()
            .map(ProcessDefinitionXml::getHash)
            .toList();
    boolean anyMatchingHash = hashes.contains(xmlHash);
    if (!anyMatchingHash) {
      // Non-existing process definition, persist it
      ProcessDefinitionXml xmlEntity =
          new ProcessDefinitionXml(null, processDefinitionId, xml, xmlHash);
      processDefinitionXmlRepository.persist(xmlEntity);
      int newVersion = hashes.size() + 1;
      Definitions newProcessDefinitionToPersist =
          Definitions.builder()
              .xmlObjectId(xmlEntity.getId())
              .processDefinitionId(processDefinitionId)
              .version(newVersion)
              .elements(parsedProcessDefinition.getElements())
              .build();
      processDefinitionRepository.persist(newProcessDefinitionToPersist);
      startNewSchedules(newProcessDefinitionToPersist);
      return newProcessDefinitionToPersist;
    } else {
      throw new WebApplicationException("Resource already exists", Response.Status.CONFLICT);
    }
  }

  private void startNewSchedules(Definitions processDefinition) {
    processDefinition
        .getStartEvents()
        .forEach(
            se ->
                se.getTimerEventDefinitions()
                    .forEach(
                        ted ->
                            processIntanceService.startNewProcessInstance(
                                processDefinition, se.getId())));
  }

  public Definitions getProcessDefinition(String processDefinitionId, long version) {
    Parameters queryparameters = Parameters.with("pdid", processDefinitionId);
    String query = QUERY_PROCESSDEFINITION_NOVERSION;
    if (version > 0) {
      queryparameters = queryparameters.and("version", version);
      query = QUERY_PROCESSDEFINITION_VERSION;
    }

    Optional<Definitions> processDefinition =
        processDefinitionRepository.find(query, queryparameters).stream()
            .max(Comparator.comparingLong(Definitions::getVersion));
    return processDefinition.orElseThrow(
        () ->
            new NotFoundException(
                "Process definition not found: " + processDefinitionId + " version: " + version));
  }

  public List<Definitions> getProcessDefinitions() {
    return processDefinitionRepository.listAll();
  }
}
