package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class XmlDefinitionsDTO extends DefinitionsTriggerDTO {

  @JsonProperty("x")
  private String xml;

  public XmlDefinitionsDTO(String xml) {
    this.xml = xml;
  }
}
