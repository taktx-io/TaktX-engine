package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEvent.class),
  @JsonSubTypes.Type(value = EventDefinition.class),
  @JsonSubTypes.Type(value = ExclusiveGateway.class),
  @JsonSubTypes.Type(value = ParallelGateway.class),
  @JsonSubTypes.Type(value = Process.class),
  @JsonSubTypes.Type(value = SubProcess.class),
  @JsonSubTypes.Type(value = SequenceFlow.class),
  @JsonSubTypes.Type(value = StartEvent.class),
  @JsonSubTypes.Type(value = IntermediateCatchEvent.class),
  @JsonSubTypes.Type(value = CallActivity.class),
  @JsonSubTypes.Type(value = ServiceTask.class),
  @JsonSubTypes.Type(value = SendTask.class),
  @JsonSubTypes.Type(value = Task.class),
  @JsonSubTypes.Type(value = TimerEventDefinition.class),
  @JsonSubTypes.Type(value = InclusiveGateway.class),
  @JsonSubTypes.Type(value = ReceiveTask.class),
  @JsonSubTypes.Type(value = MessageEventDefinition.class),
  @JsonSubTypes.Type(value = BoundaryEvent.class),
})
public abstract class BaseElement {
  private final String id;
  private final String parentId;

  protected BaseElement(@Nonnull String id, String parentId) {
    this.id = id;
    this.parentId = parentId;
  }
}
