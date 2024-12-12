package com.flomaestro.engine.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class InputOutputMapping {
  private final Set<IoVariableMapping> inputMappings;
  private final Set<IoVariableMapping> outputMappings;

  public InputOutputMapping(
      @Nonnull Set<IoVariableMapping> inputMappings,
      @Nonnull Set<IoVariableMapping> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
