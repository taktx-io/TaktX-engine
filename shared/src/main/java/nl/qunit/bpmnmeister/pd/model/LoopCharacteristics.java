package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class LoopCharacteristics {
  public static final LoopCharacteristics NONE = new LoopCharacteristics(false, "", "");
  private final boolean isSequential;
  private final String inputCollection;
  private final String inputElement;

  @JsonCreator
  public LoopCharacteristics(
      @JsonProperty("isSequential") boolean isSequential,
      @Nonnull @JsonProperty("inputCollection") String inputCollection,
      @Nonnull @JsonProperty("inputElement") String inputElement) {
    this.isSequential = isSequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LoopCharacteristics that = (LoopCharacteristics) o;
    return isSequential == that.isSequential
        && Objects.equals(inputCollection, that.inputCollection)
        && Objects.equals(inputElement, that.inputElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isSequential, inputCollection, inputElement);
  }
}
