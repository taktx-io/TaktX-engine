package nl.qunit.bpmnmeister.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.client.TaktClient;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/processes")
@Slf4j
public class TestResource {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject TaktClient taktClient;

  @GET
  public Set<String> getProcessDefinitions() {
    return taktClient.getProcessDefinitionConsumers();
  }

  @POST
  @Path("/{process}/{count}")
  public void startProcessInstance(
      @PathParam("process") String process,
      @PathParam("count") int count,
      @RequestBody String payload)
      throws JsonProcessingException {
    JsonNode jsonNode = objectMapper.readTree(payload);
    VariablesDTO variables = VariablesDTO.of("payload", jsonNode);
    for (int i = 0; i < count; i++) {
      taktClient.startProcess(process, variables);
    }
  }
}
