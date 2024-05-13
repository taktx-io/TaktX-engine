package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class LoopCharacteristics {
  public static final LoopCharacteristics NONE = new LoopCharacteristics(false, "", "", "", "");
  private final boolean sequential;
  private final String inputCollection;
  private final String inputElement;
  private final String outputCollection;
  private final String outputElement;

  @JsonCreator
  public LoopCharacteristics(
      @JsonProperty("sequential") boolean sequential,
      @Nonnull @JsonProperty("inputCollection") String inputCollection,
      @Nonnull @JsonProperty("inputElement") String inputElement,
      @Nonnull @JsonProperty("outputCollection") String outputCollection,
      @Nonnull @JsonProperty("outputElement") String outputElement) {
    this.sequential = sequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
    this.outputCollection = outputCollection;
    this.outputElement = outputElement;
  }

}
