package nl.qunit.bpmnmeister.pd.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class LoopCharacteristics2 {
  public static final LoopCharacteristics2 NONE = new LoopCharacteristics2(false, "", "", "", "");
  private final boolean sequential;
  private final String inputCollection;
  private final String inputElement;
  private final String outputCollection;
  private final String outputElement;

  public LoopCharacteristics2(
      boolean sequential,
      String inputCollection,
      String inputElement,
      String outputCollection,
      String outputElement) {
    this.sequential = sequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
    this.outputCollection = outputCollection;
    this.outputElement = outputElement;
  }
}
