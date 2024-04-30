package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlowElementTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskResponseTrigger.class)
})
@ToString
@Getter
public abstract class ProcessInstanceTrigger {

  private final ProcessInstanceKey processInstanceKey;
  private final String parentElementId;
  private final ProcessDefinition processDefinition;
  private final String elementId;
  private final Variables variables;

  protected ProcessInstanceTrigger(
      ProcessInstanceKey processInstanceKey,
      String parentElementId,
      ProcessDefinition processDefinition,
      String elementId,
      Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentElementId = parentElementId;
    this.processDefinition = processDefinition;
    this.elementId = elementId;
    this.variables = variables;
  }
}
