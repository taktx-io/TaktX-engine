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
public class ExternalTaskResponseTrigger extends ContinueFlowElementTrigger {
  public static final ExternalTaskResponseTrigger NONE =
      new ExternalTaskResponseTrigger(
          Constants.NONE_UUID,
          Constants.NONE,
          Constants.NONE_UUID,
          ExternalTaskResponseResult.NONE,
          Variables.empty());

  private final UUID elementInstanceId;
  private final ExternalTaskResponseResult externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("elementInstanceId") @Nonnull UUID elementInstanceId,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, elementInstanceId, elementId, Constants.NONE, variables);
    this.elementInstanceId = elementInstanceId;
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
