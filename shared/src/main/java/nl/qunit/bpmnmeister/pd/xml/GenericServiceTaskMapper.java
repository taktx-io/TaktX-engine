package nl.qunit.bpmnmeister.pd.xml;

import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;

public class GenericServiceTaskMapper implements ServiceTaskMapper {

  @Override
  public ServiceTask map(
      TServiceTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics) {
    return new ServiceTask(
        serviceTask.getId(),
        parentId,
        serviceTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(serviceTask.getIncoming()),
        mapQNameList(serviceTask.getOutgoing()),
        serviceTask.getImplementation(),
        loopCharacteristics,
        Map.of());
  }
}
