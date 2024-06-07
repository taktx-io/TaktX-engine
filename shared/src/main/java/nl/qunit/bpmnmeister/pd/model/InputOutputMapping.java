package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class InputOutputMapping {

  @Nonnull
  private final Set<IoVariableMapping> inputMappings;
  @Nonnull
  private final Set<IoVariableMapping> outputMappings;

  @JsonCreator
  public InputOutputMapping(
      @JsonProperty("inputMappings") @Nonnull Set<IoVariableMapping> inputMappings,
      @JsonProperty("outputMappings") @Nonnull Set<IoVariableMapping> outputMappings
  ) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
