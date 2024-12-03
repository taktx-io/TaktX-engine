package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

public interface Mapper {

  default Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::getLocalPart).collect(Collectors.toSet());
  }
}
