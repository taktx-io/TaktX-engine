package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TActivity;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.bpmn.TBoundaryEvent;
import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.bpmn.TCatchEvent;
import nl.qunit.bpmnmeister.bpmn.TEndEvent;
import nl.qunit.bpmnmeister.bpmn.TExclusiveGateway;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TIntermediateCatchEvent;
import nl.qunit.bpmnmeister.bpmn.TParallelGateway;
import nl.qunit.bpmnmeister.bpmn.TSequenceFlow;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TStartEvent;
import nl.qunit.bpmnmeister.bpmn.TSubProcess;
import nl.qunit.bpmnmeister.bpmn.TTask;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.Task;
import nl.qunit.bpmnmeister.pi.state.EventState;

public class GenericFlowElementMapper implements FlowElementMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericFlowElementMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public FlowElement map(TFlowElement tFlowElement, String parentId) {
    if (tFlowElement instanceof TSequenceFlow tSequenceFlow) {
      return mapSequenceFlow(parentId, tSequenceFlow);
    } else if (tFlowElement instanceof TActivity activity) {
      return mapActivity(tFlowElement, parentId, activity);
    } else if (tFlowElement instanceof TParallelGateway parallelGateway) {
      return new ParallelGateway(
          parallelGateway.getId(),
          parentId,
          mapQNameList(parallelGateway.getIncoming()),
          mapQNameList(parallelGateway.getOutgoing()));
    } else if (tFlowElement instanceof TExclusiveGateway exclusiveGateway) {
      return new ExclusiveGateway(
          exclusiveGateway.getId(),
          parentId,
          mapQNameList(exclusiveGateway.getIncoming()),
          mapQNameList(exclusiveGateway.getOutgoing()),
          (exclusiveGateway.getDefault() instanceof TSequenceFlow sequenceFlow)
              ? sequenceFlow.getId()
              : Constants.NONE);
    } else if (tFlowElement instanceof TCatchEvent tCatchEvent) {
      return mapCatchEvent(parentId, tCatchEvent);
    } else if (tFlowElement instanceof TEndEvent endEvent) {
      return new EndEvent(
          endEvent.getId(),
          parentId,
          mapQNameList(endEvent.getIncoming()),
          mapQNameList(endEvent.getOutgoing()));
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tFlowElement.getClass().getName());
  }

  private CatchEvent<? extends EventState> mapCatchEvent(String parentId, TCatchEvent tCatchEvent) {
    if (tCatchEvent instanceof TStartEvent startEvent) {
      return new StartEvent(
          startEvent.getId(),
          parentId,
          mapQNameList(startEvent.getIncoming()),
          mapQNameList(startEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(startEvent.getEventDefinition(), parentId));
    } else if (tCatchEvent instanceof TBoundaryEvent boundaryEvent) {
      return new BoundaryEvent(
          boundaryEvent.getId(),
          parentId,
          mapQNameList(boundaryEvent.getIncoming()),
          mapQNameList(boundaryEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(boundaryEvent.getEventDefinition(), parentId),
          boundaryEvent.getAttachedToRef().toString(),
          boundaryEvent.isCancelActivity());
    } else if (tCatchEvent instanceof TIntermediateCatchEvent intermediateCatchEvent) {
      return new IntermediateCatchEvent(
          intermediateCatchEvent.getId(),
          parentId,
          mapQNameList(intermediateCatchEvent.getIncoming()),
          mapQNameList(intermediateCatchEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(intermediateCatchEvent.getEventDefinition(), parentId));
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tCatchEvent.getClass().getName());  }

  private SequenceFlow mapSequenceFlow(String parentId, TSequenceFlow tSequenceFlow) {
    return new SequenceFlow(
        tSequenceFlow.getId(),
        parentId,
        ((TBaseElement) tSequenceFlow.getSourceRef()).getId(),
        ((TBaseElement) tSequenceFlow.getTargetRef()).getId(),
        tSequenceFlow.getConditionExpression() != null
            ? new FlowCondition(tSequenceFlow.getConditionExpression().getContent().stream()
            .map(Object::toString)
            .collect(Collectors.joining("")))
            : FlowCondition.NONE);
  }

  private FlowElement mapActivity(TFlowElement tFlowElement, String parentId,
      TActivity activity) {
    LoopCharacteristics loopCharacteristics =
        bpmnMapperFactory
            .createLoopCharacteristicsMapper()
            .map(activity.getLoopCharacteristics());
    FlowElement activityFlowElement = null;
    if (activity instanceof TServiceTask serviceTask) {
      activityFlowElement =
          bpmnMapperFactory
              .createServiceTaskMapper()
              .map(serviceTask, parentId, loopCharacteristics);
    } else if (activity instanceof TTask task) {
      activityFlowElement =
          new Task<>(
              task.getId(),
              parentId,
              mapQNameList(task.getIncoming()),
              mapQNameList(task.getOutgoing()),
              loopCharacteristics);
    } else if (activity instanceof TSubProcess subProcess) {
      Map<String, FlowElement> elements =
          subProcess.getFlowElement().stream()
              .map(
                  flowElement ->
                      bpmnMapperFactory
                          .createFlowElementMapper()
                          .map(flowElement.getValue(), tFlowElement.getId()))
              .collect(Collectors.toMap(FlowElement::getId, Function.identity()));

      activityFlowElement =
          new SubProcess(
              tFlowElement.getId(),
              parentId,
              mapQNameList(subProcess.getIncoming()),
              mapQNameList(subProcess.getOutgoing()),
              loopCharacteristics,
              new FlowElements(elements));
    } else if (activity instanceof TCallActivity callActivity) {
      activityFlowElement =
          bpmnMapperFactory
              .createCallActivityMapper()
              .map(callActivity, parentId, loopCharacteristics);
    }
    return activityFlowElement;
  }

  private static Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::toString).collect(Collectors.toSet());
  }
}
