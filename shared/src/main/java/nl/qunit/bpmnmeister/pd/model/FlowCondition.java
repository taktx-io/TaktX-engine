package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class FlowCondition {
  public static final FlowCondition NONE = new FlowCondition("");

  private final String expression;

  @JsonCreator
  public FlowCondition(@Nonnull @JsonProperty("expression") String expression) {
    this.expression = expression;
  }

}
