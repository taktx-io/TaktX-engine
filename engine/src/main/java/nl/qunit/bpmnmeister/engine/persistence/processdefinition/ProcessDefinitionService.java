package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TimerDefinitionScheduler;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;

@ApplicationScoped
@RequiredArgsConstructor
public class ProcessDefinitionService {
  private static final String QUERY_PID = "processDefinitionId = :pdid";
  private static final String QUERY_PID_STATE = "processDefinitionId = :pdid and state = :state";
  private static final String QUERY_PROCESSDEFINITION_NOVERSION = "processDefinitionId = :pdid";
  private static final String QUERY_PROCESSDEFINITION_VERSION =
      "processDefinitionId = :pdid and version = :version";

  final ProcessDefinitionRepository processDefinitionRepository;
  final ProcessDefinitionXmlRepository processDefinitionXmlRepository;
  final BpmnParser bpmnParser;
  final ProcessIntanceService processIntanceService;
  final TimerDefinitionScheduler timerDefinitionScheduler;

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
      // new process definition, persist it
      ProcessDefinitionXml xmlEntity =
          new ProcessDefinitionXml(null, processDefinitionId, xml, xmlHash);
      processDefinitionXmlRepository.persist(xmlEntity);

      deactiveActiveProcessDefinitions(processDefinitionId);

      int newVersion = hashes.size() + 1;
      Definitions newProcessDefinitionToPersist =
          persistNewProcessDefinition(
              xmlEntity, processDefinitionId, newVersion, parsedProcessDefinition);
      startNewSchedules(newProcessDefinitionToPersist);

      return newProcessDefinitionToPersist;
    } else {
      throw new WebApplicationException("Resource already exists", Response.Status.CONFLICT);
    }
  }

  private void deactiveActiveProcessDefinitions(String processDefinitionId) {
    processDefinitionRepository
        .find(
            QUERY_PID_STATE,
            Parameters.with("pdid", processDefinitionId)
                .and("state", ProcessDefinitionState.ACTIVE))
        .stream()
        .forEach(
            def -> {
              cancelActiveSchedules(def);
              processDefinitionRepository.update(
                  new Definitions(
                      def.getId(),
                      ProcessDefinitionState.INACTIVE,
                      def.getXmlObjectId(),
                      def.getProcessDefinitionId(),
                      def.getVersion(),
                      def.getElements()));
            });
  }

  private void cancelActiveSchedules(Definitions def) {
    def.getStartEvents().stream()
        .filter(se -> !se.getTimerEventDefinitions().isEmpty())
        .forEach(
            se -> {
              for (TimerEventDefinition timerEventDefinition : se.getTimerEventDefinitions()) {
                timerDefinitionScheduler.cancel(def, se, timerEventDefinition);
              }
            });
  }

  private Definitions persistNewProcessDefinition(
      ProcessDefinitionXml xmlEntity,
      String processDefinitionId,
      int newVersion,
      Definitions parsedProcessDefinition) {
    Definitions newProcessDefinitionToPersist =
        Definitions.builder()
            .xmlObjectId(xmlEntity.getId())
            .state(ProcessDefinitionState.ACTIVE)
            .processDefinitionId(processDefinitionId)
            .version(newVersion)
            .elements(parsedProcessDefinition.getElements())
            .build();

    processDefinitionRepository.persist(newProcessDefinitionToPersist);
    return newProcessDefinitionToPersist;
  }

  private void startNewSchedules(Definitions processDefinition) {
    for (StartEvent se : processDefinition.getStartEvents()) {
      for (TimerEventDefinition timerEventDefinition : se.getTimerEventDefinitions()) {
        timerDefinitionScheduler.schedule(processDefinition, se, timerEventDefinition);
      }
    }
  }

  public Optional<Definitions> getProcessDefinition(String processDefinitionId, long version) {
    Parameters queryparameters = Parameters.with("pdid", processDefinitionId);
    String query = QUERY_PROCESSDEFINITION_NOVERSION;
    if (version > 0) {
      queryparameters = queryparameters.and("version", version);
      query = QUERY_PROCESSDEFINITION_VERSION;
    }

    return processDefinitionRepository.find(query, queryparameters).stream()
        .max(Comparator.comparingLong(Definitions::getVersion));
  }

  public List<Definitions> getProcessDefinitions() {
    return processDefinitionRepository.listAll();
  }
}
