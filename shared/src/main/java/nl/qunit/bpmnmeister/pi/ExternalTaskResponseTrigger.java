package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class ExternalTaskResponseTrigger extends Trigger {
  public static final ExternalTaskResponseTrigger NONE =
      new ExternalTaskResponseTrigger(
          ProcessInstanceKey.NONE,
          BaseElementId.NONE,
          ExternalTaskResponseResult.NONE,
          Variables.EMPTY);
  private final ExternalTaskResponseResult externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(
        processInstanceKey, ProcessInstanceKey.NONE, ProcessDefinition.NONE, elementId, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
