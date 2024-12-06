package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class FlowConditionDTO {

  public static final FlowConditionDTO NONE = new FlowConditionDTO("");

  private final String expression;

  @JsonCreator
  public FlowConditionDTO(@Nonnull @JsonProperty("expression") String expression) {
    this.expression = expression;
  }
}
