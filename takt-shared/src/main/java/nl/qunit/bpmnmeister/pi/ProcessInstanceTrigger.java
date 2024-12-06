package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartNewProcessInstanceTrigger.class),
  @JsonSubTypes.Type(value = ContinueFlowElementTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskResponseTrigger.class),
  @JsonSubTypes.Type(value = TerminateTrigger.class)
})
@ToString
@Getter
public abstract class ProcessInstanceTrigger {

  private final UUID processInstanceKey;
  private final List<String> elementIdPath;
  private final VariablesDTO variables;

  protected ProcessInstanceTrigger(
      UUID processInstanceKey, List<String> elementIdPath, VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementIdPath = elementIdPath;
    this.variables = variables;
  }
}
