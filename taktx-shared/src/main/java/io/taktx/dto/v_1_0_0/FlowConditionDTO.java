package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class FlowConditionDTO {

  public static final FlowConditionDTO NONE = new FlowConditionDTO("");

  @JsonProperty("e")
  private String expression;

  public FlowConditionDTO(String expression) {
    this.expression = expression;
  }
}
