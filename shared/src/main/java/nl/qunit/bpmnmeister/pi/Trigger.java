package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlowElementTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskResponseTrigger.class)
})
public abstract class Trigger {

  public abstract BaseElementId getElementId();

  public abstract Variables getVariables();

  public abstract ProcessInstanceKey getProcessInstanceKey();

  public ProcessInstanceKey getParentProcessInstanceKey() {
    return ProcessInstanceKey.NONE;
  }

  public ProcessDefinition getProcessDefinition() {
    return ProcessDefinition.NONE;
  }
}
