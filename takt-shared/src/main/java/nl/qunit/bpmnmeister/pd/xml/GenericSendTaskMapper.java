package nl.qunit.bpmnmeister.pd.xml;

import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SendTaskDTO;

public class GenericSendTaskMapper implements SendTaskMapper {

  @Override
  public SendTaskDTO map(
      TSendTask sendTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new SendTaskDTO(
        sendTask.getId(),
        parentId,
        sendTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(sendTask.getIncoming()),
        mapQNameList(sendTask.getOutgoing()),
        sendTask.getImplementation(),
        loopCharacteristics,
        Map.of(),
        ioMapping);
  }
}
