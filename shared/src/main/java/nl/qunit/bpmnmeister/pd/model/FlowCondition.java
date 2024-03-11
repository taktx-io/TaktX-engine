package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class FlowCondition {
  public static final FlowCondition NONE = new FlowCondition("");

  private final String expression;

  @JsonCreator
  public FlowCondition(@Nonnull @JsonProperty("expression") String expression) {
    this.expression = expression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FlowCondition that = (FlowCondition) o;
    return Objects.equals(expression, that.expression);
  }

  @Override
  public int hashCode() {
    return Objects.hash(expression);
  }
}
