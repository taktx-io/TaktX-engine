package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
@ToString(callSuper = true)
public class ExternalTaskResponseTrigger extends ProcessInstanceTrigger {
  public static final ExternalTaskResponseTrigger NONE =
      new ExternalTaskResponseTrigger(
          ProcessInstanceKey.NONE,
          Constants.NONE,
          ExternalTaskResponseResult.NONE,
          Variables.empty());
  private final ExternalTaskResponseResult externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, elementId, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
