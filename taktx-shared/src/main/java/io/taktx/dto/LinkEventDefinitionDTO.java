package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LinkEventDefinitionDTO extends EventDefinitionDTO {

  @JsonProperty("a")
  private String name;

  public LinkEventDefinitionDTO(String id, String name) {
    super(id, null);
    this.name = name;
  }
}
