package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class LoopCharacteristicsDTO {
  public static final LoopCharacteristicsDTO NONE =
      new LoopCharacteristicsDTO(false, "", "", "", "");

  private boolean sequential;

  private String inputCollection;

  private String inputElement;

  private String outputCollection;

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
