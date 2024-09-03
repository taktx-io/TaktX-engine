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
public class ContinueFlowElementTrigger2 extends ProcessInstanceTrigger2
    implements SchedulableMessage<UUID> {
  public static final ContinueFlowElementTrigger2 NONE =
      new ContinueFlowElementTrigger2(
          Constants.NONE_UUID,
          List.of(),
          List.of(),
          Constants.NONE,
          VariablesDTO.empty());

  private final List<String> elementIdPath;
  private final String inputFlowId;
  private final List<UUID> elementInstanceIdPath;

  @JsonCreator
  public ContinueFlowElementTrigger2(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("parentElementIds") @Nonnull List<String> elementIdPath,
      @JsonProperty("elementInstanceId") @Nonnull List<UUID> elementInstanceIdPath,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, variables);
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.elementIdPath = elementIdPath;
    this.inputFlowId = inputFlowId;
  }

  @Override
  public UUID getRecordKey(UUID rootInstanceKey) {
    return rootInstanceKey;
  }
}
