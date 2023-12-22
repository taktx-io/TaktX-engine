package nl.qunit.bpmnmeister.engine;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import java.util.List;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;

@Path("/process-definitions")
public class ProcessDefinitionResource {
  @Inject ProcessDefinitionService processDefinitionService;

  @Startup
  void init() {}

  @POST
  @Consumes(MediaType.APPLICATION_XML)
  @Produces(MediaType.APPLICATION_JSON)
  public ProcessDefinition add(String xml) throws JAXBException {
    return processDefinitionService.persistProcessDefinition(xml);
  }

  @GET
  public List<ProcessDefinition> getAll() {
    return processDefinitionService.getProcessDefinitions();
  }

  @GET
  @Path("/{processDefinition}/{version}")
  public ProcessDefinition get(
      @PathParam("processDefinition") String processDefinition,
      @PathParam("version") long version) {
    return processDefinitionService.getProcessDefinition(processDefinition, version);
  }
}
