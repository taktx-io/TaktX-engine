package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
@ToString(callSuper = true)
public class ExternalTaskResponseTrigger2 extends ContinueFlowElementTrigger2 {
  public static final ExternalTaskResponseTrigger2 NONE =
      new ExternalTaskResponseTrigger2(
          Constants.NONE_UUID,
          Constants.NONE,
          Constants.NONE_UUID,
          ExternalTaskResponseResult2.NONE,
          VariablesDTO.empty());

  private final ExternalTaskResponseResult2 externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger2(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("elementInstanceId") @Nonnull UUID elementInstanceId,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult2 externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementInstanceId, elementId, Constants.NONE, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
