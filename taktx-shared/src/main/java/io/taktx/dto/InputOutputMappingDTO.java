package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class InputOutputMappingDTO {
  @JsonProperty("im")
  private Set<IoVariableMappingDTO> inputMappings;

  @JsonProperty("om")
  private Set<IoVariableMappingDTO> outputMappings;

  public InputOutputMappingDTO(
      Set<IoVariableMappingDTO> inputMappings, Set<IoVariableMappingDTO> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
