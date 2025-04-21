package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@ToString
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class ProcessDefinitionDTO {

  private ParsedDefinitionsDTO definitions;

  private Integer version;

  private ProcessDefinitionStateEnum state;

  public ProcessDefinitionDTO(
      ParsedDefinitionsDTO definitions, Integer version, ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }
}
