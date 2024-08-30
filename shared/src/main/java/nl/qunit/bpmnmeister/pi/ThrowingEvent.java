package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndThrowingEvent.class),
})
public abstract class ThrowingEvent {

  public static final ThrowingEvent NOOP =
      new ThrowingEvent() {
        @Override
        public ProcessInstanceState process(
            ProcessInstance processInstance, FlowNodeStates newFlowNodeStates) {
          return processInstance.getProcessInstanceState();
        }
      };

  public abstract ProcessInstanceState process(
      ProcessInstance processInstance, FlowNodeStates newFlowNodeStates);
}
