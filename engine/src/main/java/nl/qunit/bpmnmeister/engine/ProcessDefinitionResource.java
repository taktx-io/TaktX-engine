package nl.qunit.bpmnmeister.engine;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import java.util.List;
import java.util.Optional;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;

@Path("/process-definitions")
public class ProcessDefinitionResource {
  @Inject ProcessDefinitionService processDefinitionService;

  @Startup
  void init() {}

  @POST
  @Consumes(MediaType.APPLICATION_XML)
  @Produces(MediaType.APPLICATION_JSON)
  public Definitions add(String xml) throws JAXBException {
    return processDefinitionService.persistProcessDefinition(xml);
  }

  @GET
  public List<Definitions> getAll() {
    return processDefinitionService.getProcessDefinitions();
  }

  @GET
  @Path("/{processDefinition}/{version}")
  public Definitions get(
      @PathParam("processDefinition") String processDefinition,
      @PathParam("version") long version) {
    Optional<Definitions> optProcessDefinition =
        processDefinitionService.getProcessDefinition(processDefinition, version);
    if (optProcessDefinition.isPresent()) {
      return optProcessDefinition.get();
    } else {
      throw new NotFoundException();
    }
  }
}
