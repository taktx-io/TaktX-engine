package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Activity.class, name = "Activity"),
  @JsonSubTypes.Type(value = EndEvent.class, name = "EndEvent"),
  @JsonSubTypes.Type(value = EventDefinition.class, name = "EventDefinition"),
  @JsonSubTypes.Type(value = ExclusiveGateway.class, name = "ExclusiveGateway"),
  @JsonSubTypes.Type(value = ParallelGateway.class, name = "ParallelGateway"),
  @JsonSubTypes.Type(value = Process.class, name = "Process"),
  @JsonSubTypes.Type(value = SequenceFlow.class, name = "SequenceFlow"),
  @JsonSubTypes.Type(value = StartEvent.class, name = "StartEvent"),
  @JsonSubTypes.Type(value = Task.class, name = "Task"),
  @JsonSubTypes.Type(value = ServiceTask.class, name = "ServiceTask"),
  @JsonSubTypes.Type(value = TimerEventDefinition.class, name = "TimerEventDefinition")
})
public abstract class BaseElement {
  private final String id;

  protected BaseElement(String id) {
    this.id = id;
  }
}
