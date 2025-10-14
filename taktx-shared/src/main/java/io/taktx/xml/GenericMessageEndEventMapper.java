package io.taktx.xml;

import io.taktx.bpmn.TEndEvent;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageEndEventDTO;

public class GenericMessageEndEventMapper implements MessageEndEventMapper {
  @Override
  public MessageEndEventDTO map(
      TEndEvent endEvent, String parentId, InputOutputMappingDTO ioMapping) {
    throw new UnsupportedOperationException(
        "GenericScriptTaskMapper does not support mapping for tEndEvent with message definition: "
            + endEvent);
  }
}
