package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
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
          Constants.NONE_UUID, Constants.NONE_UUID, Constants.NONE, Constants.NONE, Variables.empty());

  private final String inputFlowId;

  @JsonCreator
  public ContinueFlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementInstanceId") @Nonnull UUID elementInstanceId,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, elementId, variables);
    this.inputFlowId = inputFlowId;
  }

  @Override
  public UUID getRecordKey(UUID rootInstanceKey) {
    return rootInstanceKey;
  }
}
