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
public class ExternalTaskResponseTrigger2 extends ContinueFlowElementTrigger2 {
  public static final ExternalTaskResponseTrigger2 NONE =
      new ExternalTaskResponseTrigger2(
          Constants.NONE_UUID,
          List.of(),
          List.of(),
          ExternalTaskResponseResult2.NONE,
          VariablesDTO.empty());

  private final ExternalTaskResponseResult2 externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTrigger2(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementIdPath") @Nonnull List<String> elementIdPath,
      @JsonProperty("elementInstanceIdPath") @Nonnull List<UUID> elementInstanceIdPath,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult2 externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, elementInstanceIdPath, Constants.NONE, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
