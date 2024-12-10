package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;

@Getter
@ToString(callSuper = true)
public class ContinueFlowElementTriggerDTO extends ProcessInstanceTriggerDTO
    implements SchedulableMessageDTO<UUID> {

  public static final ContinueFlowElementTriggerDTO NONE =
      new ContinueFlowElementTriggerDTO(
          Constants.NONE_UUID, List.of(), List.of(), Constants.NONE, VariablesDTO.empty());

  private final List<String> elementIdPath;
  private final String inputFlowId;
  private final List<UUID> elementInstanceIdPath;

  @JsonCreator
  public ContinueFlowElementTriggerDTO(
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
  public UUID getRecordKey(UUID processInstanceKey) {
    return processInstanceKey;
  }
}
