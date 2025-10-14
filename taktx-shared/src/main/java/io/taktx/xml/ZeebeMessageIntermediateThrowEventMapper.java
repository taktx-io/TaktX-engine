package io.taktx.xml;

import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.bpmn.TaskDefinition;
import io.taktx.bpmn.TaskHeaders;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ZeebeMessageIntermediateThrowEventMapper
    implements MessageIntermediateThrowEventMapper {
  @Override
  public MessageIntermediateThrowEventDTO map(
      TIntermediateThrowEvent intermediateThrowEvent,
      String parentId,
      InputOutputMappingDTO ioMapping) {
    Optional<TaskDefinition> taskDefinition =
        ExtensionElementHelper.extractExtensionElement(
            intermediateThrowEvent.getExtensionElements(), TaskDefinition.class);
    String taskDefinitionId = intermediateThrowEvent.getId();
    String retries = DEFAULT_RETRIES;
    if (taskDefinition.isPresent()) {
      taskDefinitionId = taskDefinition.get().getType();
      retries = taskDefinition.get().getRetries();
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            intermediateThrowEvent.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new MessageIntermediateThrowEventDTO(
        intermediateThrowEvent.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(intermediateThrowEvent.getIncoming()),
        mapQNameList(intermediateThrowEvent.getOutgoing()),
        headers,
        ioMapping);
  }
}
