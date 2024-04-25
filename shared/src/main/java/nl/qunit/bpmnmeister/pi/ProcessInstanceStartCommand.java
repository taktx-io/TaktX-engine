package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProcessInstanceStartCommand {

  private final ProcessInstanceKey parentProcessInstanceId;
  private final String parentElementId;
  private final String processDefinitionId;
  private final Variables variables;

  @JsonCreator
  public ProcessInstanceStartCommand(
      @JsonProperty("parentProcessInstanceId") ProcessInstanceKey parentProcessInstanceId,
      @JsonProperty("parentElementId") String parentElementId,
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("variables") Variables variables) {
    this.parentProcessInstanceId = parentProcessInstanceId;
    this.parentElementId = parentElementId;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }
}
