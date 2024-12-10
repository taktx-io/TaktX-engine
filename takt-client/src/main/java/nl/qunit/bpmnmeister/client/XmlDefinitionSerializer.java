package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.XmlDefinitionsDTO;

public class XmlDefinitionSerializer extends JsonSerializer<XmlDefinitionsDTO> {

  public XmlDefinitionSerializer() {
    super(XmlDefinitionsDTO.class);
  }
}
