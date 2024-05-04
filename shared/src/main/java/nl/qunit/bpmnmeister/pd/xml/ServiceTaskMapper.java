package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;

public interface ServiceTaskMapper {
  String DEFAULT_RETRIES = "3";

  ServiceTask map(
      TServiceTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics);

  default Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::toString).collect(Collectors.toSet());
  }
}
