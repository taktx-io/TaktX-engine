package com.flomaestro.client.serdes;

import com.flomaestro.takt.dto.v_1_0_0.XmlDefinitionsDTO;

public class XmlDefinitionSerializer extends JsonSerializer<XmlDefinitionsDTO> {

  public XmlDefinitionSerializer() {
    super(XmlDefinitionsDTO.class);
  }
}
