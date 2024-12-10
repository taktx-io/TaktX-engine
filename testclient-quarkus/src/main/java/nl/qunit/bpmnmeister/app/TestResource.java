package nl.qunit.bpmnmeister.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.client.TaktClient;

@Path("/processes")
@Slf4j
public class TestResource {

  @Inject TaktClient taktClient;

  @GET
  public Set<String> getProcessDefinitions() {
    return taktClient.getProcessDefinitionConsumers();
  }

  @POST
  @Path("/{process}")
  public void startProcessInstance(@PathParam("process") String process) {
    taktClient.startProcess(process);
  }
}
