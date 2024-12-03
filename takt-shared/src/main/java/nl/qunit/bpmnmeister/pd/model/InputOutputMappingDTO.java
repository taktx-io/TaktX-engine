package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class InputOutputMappingDTO {
  private final Set<IoVariableMappingDTO> inputMappings;
  private final Set<IoVariableMappingDTO> outputMappings;

  @JsonCreator
  public InputOutputMappingDTO(
      @JsonProperty("inputMappings") @Nonnull Set<IoVariableMappingDTO> inputMappings,
      @JsonProperty("outputMappings") @Nonnull Set<IoVariableMappingDTO> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
