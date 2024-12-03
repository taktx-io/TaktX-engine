package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.CallActivityDTO;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;

public class GenericCallActivityMapper implements CallActivityMapper {

  @Override
  public CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new CallActivityDTO(
        callActivity.getId(),
        parentId,
        mapQNameList(callActivity.getIncoming()),
        mapQNameList(callActivity.getOutgoing()),
        loopCharacteristics,
        callActivity.getCalledElement().toString(),
        true,
        false,
        ioMapping);
  }
}
