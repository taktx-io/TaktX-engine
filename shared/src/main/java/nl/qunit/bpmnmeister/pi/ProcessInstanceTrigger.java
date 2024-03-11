package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nonnull;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class ProcessInstanceTrigger {
  public static final ProcessInstanceTrigger NULL =
      new ProcessInstanceTrigger(
          ProcessInstanceKey.NULL,
          ProcessInstanceKey.NULL,
          ProcessDefinition.NULL,
          BaseElementId.NULL,
          false,
          BaseElementId.NULL,
          Map.of());
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final BaseElementId elementId;
  private final boolean terminate;
  private final BaseElementId inputFlowId;
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public ProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("terminate") @Nonnull Boolean terminate,
      @JsonProperty("inputFlowId") @Nonnull BaseElementId inputFlowId,
      @JsonProperty("variables") @Nonnull Map<String, JsonNode> variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processDefinition = processDefinition;
    this.elementId = elementId;
    this.terminate = terminate;
    this.inputFlowId = inputFlowId;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ProcessInstanceTrigger{"
        + "parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinition="
        + processDefinition
        + ", elementId='"
        + elementId
        + '\''
        + ", terminate="
        + terminate
        + ", inputFlowId='"
        + inputFlowId
        + '\''
        + ", variables="
        + variables
        + '}';
  }
}
