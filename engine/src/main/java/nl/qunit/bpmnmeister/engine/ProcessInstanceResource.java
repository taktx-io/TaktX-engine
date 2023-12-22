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
  @Path("/{processDefinition}/{version}/{startevent}")
  public void startNewProcessInstanceVersion(
      @PathParam("processDefinition") String processDefinition,
      @PathParam("version") long version,
      @PathParam("startevent") String startevent) {
    processInstanceService.startNewProcessInstance(processDefinition, version, startevent);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{processDefinition}/{version}")
  public void startNewProcessInstanceVersion(
      @PathParam("processDefinition") String processDefinition,
      @PathParam("version") long version) {
    processInstanceService.startNewProcessInstance(processDefinition, version, null);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{processDefinition}")
  public void startNewProcessInstanceLatest(
      @PathParam("processDefinition") String processDefinition) {
    processInstanceService.startNewProcessInstance(processDefinition, 0, null);
  }
}
