package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
public class InputOutputMappingDTO {
  private Set<IoVariableMappingDTO> inputMappings;

  private Set<IoVariableMappingDTO> outputMappings;

  public InputOutputMappingDTO(
      Set<IoVariableMappingDTO> inputMappings, Set<IoVariableMappingDTO> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
