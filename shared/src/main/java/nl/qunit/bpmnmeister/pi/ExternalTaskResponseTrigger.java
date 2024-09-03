package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
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
          List.of(),
          List.of(),
          ExternalTaskResponseResult.NONE,
          VariablesDTO.empty());

  private final ExternalTaskResponseResult externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementIdPath") @Nonnull List<String> elementIdPath,
      @JsonProperty("elementInstanceId") @Nonnull List<UUID> elementInstanceIdPath,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementInstanceIdPath, elementIdPath, Constants.NONE, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
