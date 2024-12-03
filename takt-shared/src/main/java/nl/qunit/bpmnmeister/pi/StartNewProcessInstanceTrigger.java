package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;

@Getter
@ToString(callSuper = true)
public class StartNewProcessInstanceTrigger extends StartFlowElementTrigger {

  private final UUID parentProcessInstanceKey;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final ProcessDefinitionDTO processDefinition;

  @JsonCreator
  public StartNewProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") @Nonnull UUID processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull UUID parentProcessInstanceKey,
      @JsonProperty("parentElementIdPath") @Nonnull List<String> parentElementIdPath,
      @JsonProperty("parentElementInstancePath") @Nonnull List<UUID> parentElementInstancePath,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinitionDTO processDefinition,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("variables") @Nonnull VariablesDTO variables) {
    super(processInstanceKey, List.of(elementId), Constants.NONE, variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinition = processDefinition;
  }

  public String getElementId() {
    return getElementIdPath().getFirst();
  }
}
