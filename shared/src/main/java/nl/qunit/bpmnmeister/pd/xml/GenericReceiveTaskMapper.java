package nl.qunit.bpmnmeister.pd.xml;

import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ReceiveTaskDTO;

public class GenericReceiveTaskMapper implements ReceiveTaskMapper {

  @Override
  public ReceiveTaskDTO map(
      TReceiveTask receiveTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {

    Set<String> incoming = mapQNameList(receiveTask.getIncoming());
    Set<String> outgoing = mapQNameList(receiveTask.getOutgoing());

    return new ReceiveTaskDTO(
        receiveTask.getId(),
        parentId,
        incoming,
        outgoing,
        loopCharacteristics,
        receiveTask.getMessageRef().getLocalPart(),
        ioMapping);
  }
}
