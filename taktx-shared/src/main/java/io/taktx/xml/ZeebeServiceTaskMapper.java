package io.taktx.xml;

import io.taktx.bpmn.TServiceTask;
import io.taktx.bpmn.TaskDefinition;
import io.taktx.bpmn.TaskHeaders;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ServiceTaskDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ZeebeServiceTaskMapper implements ServiceTaskMapper {

  @Override
  public ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    Optional<TaskDefinition> taskDefinition =
        ExtensionElementHelper.extractExtensionElement(
            serviceTask.getExtensionElements(), TaskDefinition.class);
    String taskDefinitionId = serviceTask.getId();
    String retries = DEFAULT_RETRIES;
    if (taskDefinition.isPresent()) {
      taskDefinitionId = taskDefinition.get().getType();
      retries = taskDefinition.get().getRetries();
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            serviceTask.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new ServiceTaskDTO(
        serviceTask.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(serviceTask.getIncoming()),
        mapQNameList(serviceTask.getOutgoing()),
        serviceTask.getImplementation(),
        loopCharacteristics,
        headers,
        ioMapping);
  }
}
