package nl.qunit.bpmnmeister.engine;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;

@Path("/process-instances")
public class ProcessInstanceResource {
  @Inject ProcessIntanceService processInstanceService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{processDefinition}/{startevent}")
  public void startNewProcessInstanceVersion(
      @PathParam("processDefinition") String processDefinition,
      @PathParam("startevent") String startevent) {
    processInstanceService.startNewProcessInstance(processDefinition, 0, startevent);
  }
}
