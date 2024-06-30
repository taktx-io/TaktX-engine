package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@EqualsAndHashCode(callSuper = true)
public class StartCommand extends DefinitionsTrigger implements SchedulableMessage<String> {

  private final UUID rootProcessInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final String elementId;
  private final String parentElementId;
  private final String processDefinitionId;
  private final Variables variables;

  @JsonCreator
  public StartCommand(
      @JsonProperty("rootProcessInstanceKey") UUID rootProcessInstanceKey,
      @JsonProperty("parentProcessInstanceKey") UUID parentProcessInstanceKey,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("parentElementId") String parentElementId,
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("variables") Variables variables) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.elementId = elementId;
    this.parentElementId = parentElementId;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }

  @Override
  public String getRecordKey(UUID rootInstanceKey) {
    return processDefinitionId;
  }
}
