package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class LoopCharacteristics {
  public static final LoopCharacteristics NONE = new LoopCharacteristics(false, "", "");
  private final boolean isSequential;
  private final String inputCollection;
  private final String inputElement;

  @JsonCreator
  public LoopCharacteristics(
      @Nonnull @JsonProperty("isSequential") Boolean isSequential,
      @Nonnull @JsonProperty("inputCollection") String inputCollection,
      @Nonnull @JsonProperty("inputElement") String inputElement) {
    this.isSequential = isSequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
  }

}
