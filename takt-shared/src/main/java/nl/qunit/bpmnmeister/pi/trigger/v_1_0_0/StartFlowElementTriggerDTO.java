package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;

@Getter
@ToString(callSuper = true)
public abstract class StartFlowElementTriggerDTO extends ProcessInstanceTriggerDTO
    implements SchedulableMessageDTO<UUID> {

  private final String inputFlowId;

  @JsonCreator
  public StartFlowElementTriggerDTO(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementId") @Nonnull List<String> elementIdPath,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, variables);
    this.inputFlowId = inputFlowId;
  }

  @Override
  public UUID getRecordKey(UUID processInstanceKey) {
    return processInstanceKey;
  }
}
