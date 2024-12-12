package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LinkEventDefinitionDTO extends EventDefinitionDTO {

  @JsonProperty("nm")
  private String name;

  public LinkEventDefinitionDTO(String id, String name) {
    super(id, Constants.NONE);
    this.name = name;
  }
}
