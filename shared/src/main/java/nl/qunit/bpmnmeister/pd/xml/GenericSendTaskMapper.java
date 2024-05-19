package nl.qunit.bpmnmeister.pd.xml;

import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.SendTask;

public class GenericSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTask map(
      TSendTask sendTask, String parentId, LoopCharacteristics loopCharacteristics) {
    return new SendTask(
        sendTask.getId(),
        parentId,
        sendTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        Map.of());
  }
}
