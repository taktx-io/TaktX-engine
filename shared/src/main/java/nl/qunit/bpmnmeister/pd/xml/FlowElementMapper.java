package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TActivity;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.bpmn.TEndEvent;
import nl.qunit.bpmnmeister.bpmn.TExclusiveGateway;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TParallelGateway;
import nl.qunit.bpmnmeister.bpmn.TSequenceFlow;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TStartEvent;
import nl.qunit.bpmnmeister.bpmn.TSubProcess;
import nl.qunit.bpmnmeister.bpmn.TTask;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.FlowCondition;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.Task;

public class FlowElementMapper {
  private FlowElementMapper() {}

  public static FlowElement map(TFlowElement tFlowElement, BaseElementId parentId) {
    if (tFlowElement instanceof TSequenceFlow tSequenceFlow) {
      return new SequenceFlow(
          new BaseElementId(tSequenceFlow.getId()),
          parentId,
          new BaseElementId(((TBaseElement) tSequenceFlow.getSourceRef()).getId()),
          new BaseElementId(((TBaseElement) tSequenceFlow.getTargetRef()).getId()),
          tSequenceFlow.getConditionExpression() != null
              ? new FlowCondition(tSequenceFlow.getConditionExpression().getContent().toString())
              : FlowCondition.NONE);
    } else if (tFlowElement instanceof TActivity activity) {
      LoopCharacteristics loopCharacteristics =
          LoopCharacteristicsMapper.mapLoopCharacteristics(activity.getLoopCharacteristics());
      FlowElement activityFlowElement = null;
      if (activity instanceof TServiceTask serviceTask) {
        activityFlowElement =
            new ServiceTask(
                new BaseElementId(serviceTask.getId()),
                parentId,
                mapQNameList(serviceTask.getIncoming()),
                mapQNameList(serviceTask.getOutgoing()),
                serviceTask.getImplementation(),
                loopCharacteristics);
      } else if (activity instanceof TTask task) {
        activityFlowElement =
            new Task(
                new BaseElementId(task.getId()),
                parentId,
                mapQNameList(task.getIncoming()),
                mapQNameList(task.getOutgoing()),
                loopCharacteristics);
      } else if (activity instanceof TSubProcess subProcess) {
        Map<BaseElementId, FlowElement> elements =
            subProcess.getFlowElement().stream()
                .map(
                    flowElement ->
                        FlowElementMapper.map(flowElement.getValue(), new BaseElementId(tFlowElement.getId())))
                .collect(Collectors.toMap(FlowElement::getId, Function.identity()));

        activityFlowElement =
            new SubProcess(
                new BaseElementId(tFlowElement.getId()),
                parentId,
                mapQNameList(subProcess.getIncoming()),
                mapQNameList(subProcess.getOutgoing()),
                loopCharacteristics,
                new FlowElements(elements));
      }
      return activityFlowElement;
    } else if (tFlowElement instanceof TParallelGateway parallelGateway) {
      return new ParallelGateway(
          new BaseElementId(parallelGateway.getId()),
          parentId,
          mapQNameList(parallelGateway.getIncoming()),
          mapQNameList(parallelGateway.getOutgoing()));
    } else if (tFlowElement instanceof TExclusiveGateway exclusiveGateway) {
      return new ExclusiveGateway(
          new BaseElementId(exclusiveGateway.getId()),
          parentId,
          mapQNameList(exclusiveGateway.getIncoming()),
          mapQNameList(exclusiveGateway.getOutgoing()));
    } else if (tFlowElement instanceof TStartEvent startEvent) {
      return new StartEvent(
          new BaseElementId(startEvent.getId()),
          parentId,
          mapQNameList(startEvent.getIncoming()),
          mapQNameList(startEvent.getOutgoing()),
          EventDefinitionMapper.map(startEvent.getEventDefinition(), parentId));
    } else if (tFlowElement instanceof TEndEvent endEvent) {
      return new EndEvent(
          new BaseElementId(endEvent.getId()),
          parentId,
          mapQNameList(endEvent.getIncoming()),
          mapQNameList(endEvent.getOutgoing()));
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tFlowElement.getClass().getName());
  }

  private static Set<BaseElementId> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(i -> new BaseElementId(i.toString())).collect(Collectors.toSet());
  }
}
