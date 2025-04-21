package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LinkEventDefinitionDTO extends EventDefinitionDTO {

  private String name;

  public LinkEventDefinitionDTO(String id, String name) {
    super(id, null);
    this.name = name;
  }
}
