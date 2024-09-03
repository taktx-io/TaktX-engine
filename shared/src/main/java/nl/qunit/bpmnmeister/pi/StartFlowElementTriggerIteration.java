package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
@ToString(callSuper = true)
public class StartFlowElementTriggerIteration extends StartFlowElementTrigger {
  public static final StartFlowElementTriggerIteration NONE =
      new StartFlowElementTriggerIteration(
          Constants.NONE_UUID,
          Constants.NONE_UUID,
          Constants.NONE_UUID,
          Constants.NONE,
          Constants.NONE,
          VariablesDTO.empty());

  private final UUID parentElementInstance;
  private final UUID elementInstanceId;

  @JsonCreator
  public StartFlowElementTriggerIteration(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("parentElementInstance") @Nonnull UUID parentElementInstance,
      @JsonProperty("elementInstanceId") @Nonnull UUID elementInstanceId,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, List.of(), elementId, variables);
    this.parentElementInstance = parentElementInstance;
    this.elementInstanceId = elementInstanceId;
  }
}
