package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.function.Supplier;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlowElementTrigger.class),
  @JsonSubTypes.Type(value = FlowElementNewProcessInstanceTrigger.class),
  @JsonSubTypes.Type(value = ExternalTaskTrigger.class)
})
public abstract class Trigger {

  public abstract BaseElementId getElementId();

  public abstract Variables getVariables();

  public abstract ProcessInstanceKey getProcessInstanceKey();

  public abstract ProcessInstance getProcessInstance(
      Supplier<ProcessInstance> processInstanceSupplier);
}
