package nl.qunit.bpmnmeister.pd.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TaskDefinition;
import nl.qunit.bpmnmeister.bpmn.TaskHeaders;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;

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
