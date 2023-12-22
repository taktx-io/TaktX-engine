package nl.qunit.bpmnmeister.engine;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;

import java.util.List;

@Path("/process-instances")
public class ProcessInstanceResource {
  @Inject ProcessIntanceService processInstanceService;

  @POST
  @Consumes(MediaType.APPLICATION_XML)
  @Path("/{processDefinition}/{version}")
  public void startNewProcessInstanceVersion(@PathParam("processDefinition") String processDefinition,
                                      @PathParam("version") long version) {
    processInstanceService.startNewProcessInstance(processDefinition, version);
  }
  @POST
  @Consumes(MediaType.APPLICATION_XML)
  @Path("/{processDefinition}")
  public void startNewProcessInstanceLatest(@PathParam("processDefinition") String processDefinition) {
    processInstanceService.startNewProcessInstance(processDefinition, 0);
  }

}
