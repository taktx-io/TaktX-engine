package nl.qunit.bpmnmeister.pd.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TaskDefinition;
import nl.qunit.bpmnmeister.bpmn.TaskHeaders;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;

public class ZeebeServiceTaskMapper implements ServiceTaskMapper {

  @Override
  public ServiceTask map(
      TServiceTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics) {
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

    return new ServiceTask(
        serviceTask.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(serviceTask.getIncoming()),
        mapQNameList(serviceTask.getOutgoing()),
        serviceTask.getImplementation(),
        loopCharacteristics,
        headers);
  }
}
