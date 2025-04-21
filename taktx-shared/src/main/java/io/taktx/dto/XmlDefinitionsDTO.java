package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class XmlDefinitionsDTO extends DefinitionsTriggerDTO {

  private String xml;

  public XmlDefinitionsDTO(String xml) {
    this.xml = xml;
  }
}
