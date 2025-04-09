package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class LoopCharacteristicsDTO {
  public static final LoopCharacteristicsDTO NONE =
      new LoopCharacteristicsDTO(false, "", "", "", "");

  @JsonProperty("s")
  private boolean sequential;

  @JsonProperty("ic")
  private String inputCollection;

  @JsonProperty("ie")
  private String inputElement;

  @JsonProperty("oc")
  private String outputCollection;

  @JsonProperty("oe")
  private String outputElement;

  public LoopCharacteristicsDTO(
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
