package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pd.model.v_1_0_0.XmlDefinitionsDTO;

public class XmlDefinitionSerializer extends JsonSerializer<XmlDefinitionsDTO> {

  public XmlDefinitionSerializer() {
    super(XmlDefinitionsDTO.class);
  }
}
