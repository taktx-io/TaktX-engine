package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.*;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;

@ApplicationScoped
public class FlowElementMapper {
  public FlowElement map(TFlowElement tFlowElement) {
    if (tFlowElement instanceof TSequenceFlow tSequenceFlow) {
      return SequenceFlow.builder()
          .id(tSequenceFlow.getId())
          .source(((TBaseElement) tSequenceFlow.getSourceRef()).getId())
          .target(((TBaseElement) tSequenceFlow.getTargetRef()).getId())
          .condition(
              tSequenceFlow.getConditionExpression() != null
                  ? tSequenceFlow.getConditionExpression().getContent().toString()
                  : null)
          .build();
    } else if (tFlowElement instanceof TServiceTask serviceTask) {
      return ServiceTask.builder()
          .id(serviceTask.getId())
          .incoming(mapQNameList(serviceTask.getIncoming()))
          .outgoing(mapQNameList(serviceTask.getOutgoing()))
          .build();
    } else if (tFlowElement instanceof TTask task) {
      return Task.builder()
          .id(task.getId())
          .incoming(mapQNameList(task.getIncoming()))
          .outgoing(mapQNameList(task.getOutgoing()))
          .build();
    } else if (tFlowElement instanceof TParallelGateway parallelGateway) {
      return ParallelGateway.builder()
          .id(parallelGateway.getId())
          .incoming(mapQNameList(parallelGateway.getIncoming()))
          .outgoing(mapQNameList(parallelGateway.getOutgoing()))
          .build();
    } else if (tFlowElement instanceof TExclusiveGateway exclusiveGateway) {
      return ExclusiveGateway.builder()
          .id(exclusiveGateway.getId())
          .incoming(mapQNameList(exclusiveGateway.getIncoming()))
          .outgoing(mapQNameList(exclusiveGateway.getOutgoing()))
          .build();
    } else if (tFlowElement instanceof TStartEvent startEvent) {
      return StartEvent.builder()
          .id(startEvent.getId())
          .incoming(mapQNameList(startEvent.getIncoming()))
          .outgoing(mapQNameList(startEvent.getOutgoing()))
          .build();
    } else if (tFlowElement instanceof TEndEvent endEvent) {
      return EndEvent.builder()
          .id(endEvent.getId())
          .incoming(mapQNameList(endEvent.getIncoming()))
          .outgoing(mapQNameList(endEvent.getOutgoing()))
          .build();
    }

    throw new RuntimeException("Unknown flow element type: " + tFlowElement.getClass().getName());
  }

  private Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::toString).collect(Collectors.toSet());
  }
}
