package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@EqualsAndHashCode(callSuper = true)
public class StartCommand extends DefinitionsTrigger implements SchedulableMessage<String> {

  private final ProcessInstanceKey parentProcessInstanceKey;
  private final String parentElementId;
  private final String processDefinitionId;
  private final Variables variables;

  @JsonCreator
  public StartCommand(
      @JsonProperty("parentProcessInstanceKey") ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("parentElementId") String parentElementId,
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("variables") Variables variables) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementId = parentElementId;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }

  @Override
  public String getRecordKey() {
    return processDefinitionId;
  }
}
