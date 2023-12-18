package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.*;
import nl.qunit.bpmnmeister.model.processdefinition.*;

@ApplicationScoped
public class BpmnElementMapper {
  public Optional<BpmnElement> map(TFlowElement flowElement) {
    if (flowElement instanceof TStartEvent tStartEvent) {
      return Optional.of(new StartEvent(flowElement.getId(), mapOutgoing(tStartEvent)));
    } else if (flowElement instanceof TServiceTask tServiceTask) {
      return Optional.of(new ServiceTask(flowElement.getId(), mapOutgoing(tServiceTask)));
    } else if (flowElement instanceof TTask ttask) {
      return Optional.of(new Task(flowElement.getId(), mapOutgoing(ttask)));
    } else if (flowElement instanceof TEndEvent) {
      return Optional.of(new EndEvent(flowElement.getId(), Set.of()));
    }
    return Optional.empty();
  }

  private Set<String> mapOutgoing(TFlowNode tFlowNode) {
    return tFlowNode.getOutgoing().stream().map(QName::getLocalPart).collect(Collectors.toSet());
  }
}
