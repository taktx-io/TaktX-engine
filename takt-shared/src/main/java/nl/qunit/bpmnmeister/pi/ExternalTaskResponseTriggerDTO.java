package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

@Getter
@ToString(callSuper = true)
public class ExternalTaskResponseTriggerDTO extends ContinueFlowElementTriggerDTO {

  private final ExternalTaskResponseResultDTO externalTaskResponseResult;

  @JsonCreator
  public ExternalTaskResponseTriggerDTO(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("elementIdPath") @Nonnull List<String> elementIdPath,
      @JsonProperty("elementInstanceIdPath") @Nonnull List<UUID> elementInstanceIdPath,
      @JsonProperty("externalTaskResponseResult") @Nonnull
      ExternalTaskResponseResultDTO externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, elementInstanceIdPath, Constants.NONE, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
