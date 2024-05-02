package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public interface CallActivityMapper {

  CallActivity map(
      TCallActivity callActivity, String parentId, LoopCharacteristics loopCharacteristics);

  default Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::toString).collect(Collectors.toSet());
  }
}
