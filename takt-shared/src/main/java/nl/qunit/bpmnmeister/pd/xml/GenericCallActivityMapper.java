package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.CallActivityDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;

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
