package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@ToString
@NoArgsConstructor
public class ProcessDefinitionDTO {

  @JsonProperty("dfn")
  private ParsedDefinitionsDTO definitions;

  @JsonProperty("vsn")
  private Integer version;

  @JsonProperty("stt")
  private ProcessDefinitionStateEnum state;

  public ProcessDefinitionDTO(
      ParsedDefinitionsDTO definitions, Integer version, ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }
}
