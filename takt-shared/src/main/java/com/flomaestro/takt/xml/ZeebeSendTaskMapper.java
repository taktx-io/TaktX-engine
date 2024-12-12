package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TSendTask;
import com.flomaestro.bpmn.TaskDefinition;
import com.flomaestro.bpmn.TaskHeaders;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ZeebeSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTaskDTO map(
      TSendTask sendTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    Optional<TaskDefinition> taskDefinition =
        ExtensionElementHelper.extractExtensionElement(
            sendTask.getExtensionElements(), TaskDefinition.class);
    String taskDefinitionId = sendTask.getId();
    String retries = DEFAULT_RETRIES;
    if (taskDefinition.isPresent()) {
      taskDefinitionId = taskDefinition.get().getType();
      retries = taskDefinition.get().getRetries();
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            sendTask.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new SendTaskDTO(
        sendTask.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        headers,
        ioMapping);
  }
}
