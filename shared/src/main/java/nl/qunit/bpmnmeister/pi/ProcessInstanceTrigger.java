package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartFlowElementTrigger.class),
  @JsonSubTypes.Type(value = ContinueFlowElementTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskResponseTrigger.class),
  @JsonSubTypes.Type(value = StartNewProcessInstanceTrigger.class),
  @JsonSubTypes.Type(value = TerminateTrigger.class)
})
@ToString
@Getter
public abstract class ProcessInstanceTrigger {

  private final UUID processInstanceKey;
  private final String elementId;
  private final Variables variables;

  protected ProcessInstanceTrigger(
      UUID processInstanceKey,
      String elementId,
      Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.variables = variables;
  }
}
