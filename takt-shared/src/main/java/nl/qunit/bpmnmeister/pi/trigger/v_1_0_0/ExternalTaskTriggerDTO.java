package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;

@Getter
@ToString
public class ExternalTaskTriggerDTO implements SchedulableMessageDTO<UUID> {

  private final UUID processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final List<String> elementIdPath;
  private final String externalTaskId;
  private final List<UUID> elementInstanceIdPath;
  private final VariablesDTO variables;

  @JsonCreator
  public ExternalTaskTriggerDTO(
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("parentElementIds") List<String> elementIdPath,
      @JsonProperty("externalTaskId") String externalTaskId,
      @JsonProperty("elementInstanceId") List<UUID> elementInstanceIdPath,
      @JsonProperty("variables") VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.elementIdPath = elementIdPath;
    this.externalTaskId = externalTaskId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.variables = variables;
  }

  @JsonIgnore
  @Override
  public UUID getRecordKey(UUID processInstanceKey) {
    return this.processInstanceKey;
  }
}
