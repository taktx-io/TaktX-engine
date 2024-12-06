package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SequenceFlowDTO extends FlowElementDTO {

  String source;
  String target;
  FlowConditionDTO condition;

  @JsonCreator
  public SequenceFlowDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("source") String source,
      @Nonnull @JsonProperty("target") String target,
      @Nonnull @JsonProperty("condition") FlowConditionDTO condition) {
    super(id, parentId);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }
}
