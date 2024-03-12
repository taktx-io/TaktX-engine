package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
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
  @JsonSubTypes.Type(value = Task.class),
  @JsonSubTypes.Type(value = ServiceTask.class),
  @JsonSubTypes.Type(value = TimerEventDefinition.class)
})
public abstract class BaseElement {
  private final BaseElementId id;
  private final BaseElementId parentId;

  protected BaseElement(@Nonnull BaseElementId id, @Nonnull BaseElementId parentId) {
    this.id = id;
    this.parentId = parentId;
  }
}
