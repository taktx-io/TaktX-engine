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
public class ContinueFlowElementTrigger extends ProcessInstanceTrigger
    implements SchedulableMessage<UUID> {
  public static final ContinueFlowElementTrigger NONE =
      new ContinueFlowElementTrigger(
          Constants.NONE_UUID,
          List.of(),
          List.of(),
          Constants.NONE,
          VariablesDTO.empty());

  private final List<UUID> elementInstanceIdPath;
  private final String inputFlowId;

  @JsonCreator
  public ContinueFlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementInstanceIdPath") @Nonnull List<UUID> elementInstanceIdPath,
      @JsonProperty("elementIdPath") @Nonnull List<String> elementIdPath,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, variables);
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.inputFlowId = inputFlowId;
  }

  @Override
  public UUID getRecordKey(UUID rootInstanceKey) {
    return rootInstanceKey;
  }
}
