package io.taktx.client.serdes;

import io.taktx.dto.XmlDefinitionsDTO;

public class XmlDefinitionSerializer extends JsonSerializer<XmlDefinitionsDTO> {

  public XmlDefinitionSerializer() {
    super(XmlDefinitionsDTO.class);
  }
}
