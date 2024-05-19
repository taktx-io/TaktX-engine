package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.SendTask;

public interface SendTaskMapper {
  String DEFAULT_RETRIES = "3";

  SendTask map(
      TSendTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics);

  default Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::toString).collect(Collectors.toSet());
  }
}
