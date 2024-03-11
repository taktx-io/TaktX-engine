package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class SequenceFlow extends FlowElement {
  BaseElementId source;
  BaseElementId target;
  FlowCondition condition;

  @JsonCreator
  public SequenceFlow(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("source") BaseElementId source,
      @Nonnull @JsonProperty("target") BaseElementId target,
      @Nonnull @JsonProperty("condition") FlowCondition condition) {
    super(id, parentId);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }

  @JsonIgnore
  public boolean testCondition() {
    return true;
  }
}
