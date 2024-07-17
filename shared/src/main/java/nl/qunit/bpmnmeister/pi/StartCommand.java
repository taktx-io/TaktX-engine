package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
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
  private final UUID processInstanceKey;
  private final String elementId;
  private final String parentElementId;
  private final UUID parentElementInstanceId;
  private final String processDefinitionId;
  private final Variables variables;

  @JsonCreator
  public StartCommand(
      @Nonnull @JsonProperty("rootProcessInstanceKey") UUID rootProcessInstanceKey,
      @Nonnull @JsonProperty("parentProcessInstanceKey") UUID parentProcessInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("parentElementInstanceId") UUID parentElementInstanceId,
      @Nonnull @JsonProperty("processDefinitionId") String processDefinitionId,
      @Nonnull @JsonProperty("variables") Variables variables) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.parentElementId = parentElementId;
    this.parentElementInstanceId = parentElementInstanceId;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }

  @Override
  public String getRecordKey(UUID rootInstanceKey) {
    return processDefinitionId;
  }
}
