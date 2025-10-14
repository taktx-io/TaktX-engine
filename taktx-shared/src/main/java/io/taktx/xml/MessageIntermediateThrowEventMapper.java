package io.taktx.xml;

import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;

public interface MessageIntermediateThrowEventMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  MessageIntermediateThrowEventDTO map(
      TIntermediateThrowEvent intermediateThrowEvent,
      String parentId,
      InputOutputMappingDTO ioMapping);
}
