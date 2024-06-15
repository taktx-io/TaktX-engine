package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@ToString(callSuper = true)
public class FlowElementTrigger extends ProcessInstanceTrigger
    implements SchedulableMessage<ProcessInstanceKey> {
  public static final FlowElementTrigger NONE =
      new FlowElementTrigger(
          ProcessInstanceKey.NONE, Constants.NONE, Constants.NONE, Variables.empty());

  private final String inputFlowId;

  @JsonCreator
  public FlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, elementId, variables);
    this.inputFlowId = inputFlowId;
  }

  @Override
  public ProcessInstanceKey getRecordKey() {
    return getProcessInstanceKey();
  }
}
