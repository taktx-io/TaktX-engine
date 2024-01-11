package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class SequenceFlow extends FlowElement {
  String source;
  String target;
  String condition;

  @JsonCreator
  public SequenceFlow(
      @JsonProperty("id") String id,
      @JsonProperty("source") String source,
      @JsonProperty("target") String target,
      @JsonProperty("condition") String condition) {
    super(id);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }
}
