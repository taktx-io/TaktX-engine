package nl.qunit.bpmnmeister.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.resteasy.reactive.RestPath;

@Path("/process")
public class ProcessResource {
  @Inject ObjectMapper objectMapper;

  @Inject
  @Channel("process-instance-start-command-outgoing")
  Emitter<ProcessInstanceStartCommand> startCommandEmitter;

  @POST
  @Path("/{processId}/{gen}/{version}/{elementId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void start(
      @RestPath String processId,
      @RestPath Integer gen,
      @RestPath Integer version,
      @RestPath String elementId,
      String variables) {
    // Convert Json string to Map of variables with JsonNode values
    Map<String, JsonNode> variablesMap;
    try {
      variablesMap = objectMapper.readValue(variables, LinkedHashMap.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse variables", e);
    }
    ProcessDefinitionKey processDefinitionKey =
        new ProcessDefinitionKey(new BaseElementId(processId), gen, version);
    ProcessInstanceStartCommand startCommand =
        new ProcessInstanceStartCommand(
            processDefinitionKey, new BaseElementId(elementId), new Variables(variablesMap));
    startCommandEmitter.send(KafkaRecord.of(processDefinitionKey, startCommand));
  }
}
