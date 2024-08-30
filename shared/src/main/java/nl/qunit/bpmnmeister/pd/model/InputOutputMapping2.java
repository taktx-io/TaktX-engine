package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class InputOutputMapping2 {
  private final Set<IoVariableMapping2> inputMappings;
  private final Set<IoVariableMapping2> outputMappings;

  public InputOutputMapping2(
      @Nonnull Set<IoVariableMapping2> inputMappings,
      @Nonnull Set<IoVariableMapping2> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
