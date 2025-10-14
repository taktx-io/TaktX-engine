package io.taktx.xml;

import io.taktx.bpmn.TEndEvent;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageEndEventDTO;

public interface MessageEndEventMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  MessageEndEventDTO map(TEndEvent endEvent, String parentId, InputOutputMappingDTO ioMapping);
}
