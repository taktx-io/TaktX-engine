package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@ToString(callSuper = true)
public class StartFlowElementTrigger extends ProcessInstanceTrigger
    implements SchedulableMessage<UUID> {
  public static final StartFlowElementTrigger NONE =
      new StartFlowElementTrigger(
          Constants.NONE_UUID, List.of(), Constants.NONE, VariablesDTO.empty());

  private final List<String> elementIdPath;
  private final String inputFlowId;

  @JsonCreator
  public StartFlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementIdPath") @Nonnull List<String> elementIdPath,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, variables);
    this.elementIdPath = elementIdPath;
    this.inputFlowId = inputFlowId;
  }

  @Override
  public UUID getRecordKey(UUID processInstanceKey) {
    return processInstanceKey;
  }
}
