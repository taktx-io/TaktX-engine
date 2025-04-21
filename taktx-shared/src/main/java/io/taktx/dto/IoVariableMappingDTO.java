package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class IoVariableMappingDTO {

  private String source;

  private String target;

  public IoVariableMappingDTO(String source, String target) {
    this.source = source;
    this.target = target;
  }
}
