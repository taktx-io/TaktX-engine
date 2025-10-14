package io.taktx.xml;

import io.taktx.bpmn.TEndEvent;
import io.taktx.bpmn.TaskDefinition;
import io.taktx.bpmn.TaskHeaders;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageEndEventDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ZeebeMessageEndEventMapper implements MessageEndEventMapper {
  @Override
  public MessageEndEventDTO map(
      TEndEvent endEvent, String parentId, InputOutputMappingDTO ioMapping) {
    Optional<TaskDefinition> taskDefinition =
        ExtensionElementHelper.extractExtensionElement(
            endEvent.getExtensionElements(), TaskDefinition.class);
    String taskDefinitionId = endEvent.getId();
    String retries = DEFAULT_RETRIES;
    if (taskDefinition.isPresent()) {
      taskDefinitionId = taskDefinition.get().getType();
      retries = taskDefinition.get().getRetries();
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            endEvent.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new MessageEndEventDTO(
        endEvent.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(endEvent.getIncoming()),
        mapQNameList(endEvent.getOutgoing()),
        headers,
        ioMapping);
  }
}
