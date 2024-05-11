package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Getter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlowElementTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskResponseTrigger.class),
  @JsonSubTypes.Type(value = StartNewProcessInstanceTrigger.class),
  @JsonSubTypes.Type(value = TerminateProcessInstanceTrigger.class)
})
@ToString
@Getter
public abstract class ProcessInstanceTrigger {

  private final ProcessInstanceKey processInstanceKey;
  private final String elementId;
  private final Variables variables;

  protected ProcessInstanceTrigger(
      ProcessInstanceKey processInstanceKey, String elementId, Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.variables = variables;
  }
}
