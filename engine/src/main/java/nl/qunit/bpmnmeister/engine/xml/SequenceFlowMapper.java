package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TSequenceFlow;
import nl.qunit.bpmnmeister.model.processdefinition.SequenceFlow;

@ApplicationScoped
public class SequenceFlowMapper {
  public Optional<SequenceFlow> map(TFlowElement flowElement) {
    if (flowElement instanceof TSequenceFlow sequenceFlow) {
      return Optional.of(
          new SequenceFlow(
              sequenceFlow.getId(),
              ((TBaseElement) (sequenceFlow.getTargetRef())).getId(),
              processInstance -> true));
    }
    return Optional.empty();
  }
}
