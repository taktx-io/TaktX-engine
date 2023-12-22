package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBException;
import nl.qunit.bpmnmeister.engine.ProcessInstanceResource;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProcessIntanceService {
  private static final String QUERY_HASH = "processDefinitionId = :pdid and hash = :hash";
  private static final String QUERY_PROCESSDEFINITION_VERSION =
      "processDefinitionId = :pdid and version = :version";

  @Inject
  ProcessDefinitionService processDefinitionService;
  @Inject
  ProcessDefinitionRepository processDefinitionRepository;
  @Inject
  ProcessInstanceRepository processInstanceRepository;

  public void startNewProcessInstance(String processDefinitionId, long version) {
    ProcessDefinition processDefinition1 = processDefinitionService.getProcessDefinition(processDefinitionId, version);
  }
}
