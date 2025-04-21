package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = Shape.ARRAY)
public class FlowConditionDTO {

  public static final FlowConditionDTO NONE = new FlowConditionDTO("");

  private String expression;

  public FlowConditionDTO(String expression) {
    this.expression = expression;
  }
}
