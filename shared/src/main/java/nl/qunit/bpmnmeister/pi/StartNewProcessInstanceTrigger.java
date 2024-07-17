package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
@ToString(callSuper = true)
public class StartNewProcessInstanceTrigger extends StartFlowElementTrigger {

  @Nonnull
  private final UUID rootInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final ProcessDefinition processDefinition;
  private final String parentElementId;

  @JsonCreator
  public StartNewProcessInstanceTrigger(
      @JsonProperty("rootInstanceKey") @Nonnull UUID rootInstanceKey,
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull UUID parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("parentElementId") @Nonnull String parentElementId,
      @JsonProperty("sourceInstanceId") @Nonnull UUID sourceInstanceId,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, sourceInstanceId, elementId, inputFlowId, variables);
    this.rootInstanceKey = rootInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processDefinition = processDefinition;
    this.parentElementId = parentElementId;
  }
}
