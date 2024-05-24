package nl.qunit.bpmnmeister.pd.xml;

import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;

public class GenericReceiveTaskMapper implements ReceiveTaskMapper {

  @Override
  public ReceiveTask map(
      TReceiveTask receiveTask, String parentId, LoopCharacteristics loopCharacteristics) {

    Set<String> incoming = mapQNameList(receiveTask.getIncoming());
    Set<String> outgoing = mapQNameList(receiveTask.getOutgoing());

    return new ReceiveTask(
        receiveTask.getId(),
        parentId,
        incoming,
        outgoing,
        loopCharacteristics,
        receiveTask.getMessageRef().getLocalPart());
  }
}
