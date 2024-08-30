package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;

@Getter
@ToString(callSuper = true)
public class StartNewProcessInstanceTrigger2 extends ProcessInstanceTrigger2 {

  private final ProcessDefinitionDTO processDefinition;

  @JsonCreator
  public StartNewProcessInstanceTrigger2(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinitionDTO processDefinition,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, elementId, variables);
    this.processDefinition = processDefinition;
  }
}
