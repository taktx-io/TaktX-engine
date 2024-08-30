package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartNewProcessInstanceTrigger2.class),
})
@ToString
@Getter
public abstract class ProcessInstanceTrigger2 {

  private final UUID processInstanceKey;
  private final String elementId;
  private final VariablesDTO variables;

  protected ProcessInstanceTrigger2(
      UUID processInstanceKey, String elementId, VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.variables = variables;
  }
}
