package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.function.Supplier;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;

@Getter
public class ExternalTaskResponseTrigger extends Trigger {
  public static final ExternalTaskResponseTrigger NONE =
      new ExternalTaskResponseTrigger(
          ProcessInstanceKey.NONE,
          BaseElementId.NONE,
          ExternalTaskResponseResult.NONE,
          Variables.EMPTY);
  private final ProcessInstanceKey processInstanceKey;
  private final BaseElementId elementId;
  private final ExternalTaskResponseResult externalTaskResponseResult;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskResponseTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("externalTaskResponseResult") @Nonnull
          ExternalTaskResponseResult externalTaskResponseResult,
      @JsonProperty("variables") @Nonnull Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.externalTaskResponseResult = externalTaskResponseResult;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ProcessInstanceTrigger{"
        + ", processInstanceKey="
        + processInstanceKey
        + ", elementId='"
        + elementId
        + ", variables="
        + variables
        + '}';
  }

  @Override
  public ProcessInstance getProcessInstance(Supplier<ProcessInstance> processInstanceSupplier) {
    return processInstanceSupplier.get();
  }
}
