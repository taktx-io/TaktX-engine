package nl.qunit.bpmnmeister.pd.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.bpmn.TaskDefinition;
import nl.qunit.bpmnmeister.bpmn.TaskHeaders;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.SendTask;

public class ZeebeSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTask map(
      TSendTask sendTask, String parentId, LoopCharacteristics loopCharacteristics) {
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

    return new SendTask(
        sendTask.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        headers);
  }
}
