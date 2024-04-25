package nl.qunit.bpmnmeister.pd.xml;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import nl.qunit.bpmnmeister.bpmn.TActivity;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.bpmn.TEndEvent;
import nl.qunit.bpmnmeister.bpmn.TExclusiveGateway;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TParallelGateway;
import nl.qunit.bpmnmeister.bpmn.TSequenceFlow;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TStartEvent;
import nl.qunit.bpmnmeister.bpmn.TSubProcess;
import nl.qunit.bpmnmeister.bpmn.TTask;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
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

  public static FlowElement map(TFlowElement tFlowElement, String parentId) {
    if (tFlowElement instanceof TSequenceFlow tSequenceFlow) {
      return new SequenceFlow(
          new String(tSequenceFlow.getId()),
          parentId,
          new String(((TBaseElement) tSequenceFlow.getSourceRef()).getId()),
          new String(((TBaseElement) tSequenceFlow.getTargetRef()).getId()),
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
                new String(serviceTask.getId()),
                parentId,
                mapQNameList(serviceTask.getIncoming()),
                mapQNameList(serviceTask.getOutgoing()),
                serviceTask.getImplementation(),
                loopCharacteristics);
      } else if (activity instanceof TTask task) {
        activityFlowElement =
            new Task(
                new String(task.getId()),
                parentId,
                mapQNameList(task.getIncoming()),
                mapQNameList(task.getOutgoing()),
                loopCharacteristics);
      } else if (activity instanceof TSubProcess subProcess) {
        Map<String, FlowElement> elements =
            subProcess.getFlowElement().stream()
                .map(
                    flowElement ->
                        FlowElementMapper.map(
                            flowElement.getValue(), new String(tFlowElement.getId())))
                .collect(Collectors.toMap(FlowElement::getId, Function.identity()));

        activityFlowElement =
            new SubProcess(
                new String(tFlowElement.getId()),
                parentId,
                mapQNameList(subProcess.getIncoming()),
                mapQNameList(subProcess.getOutgoing()),
                loopCharacteristics,
                new FlowElements(elements));
      } else if (activity instanceof TCallActivity callActivity) {
        activityFlowElement =
            new CallActivity(
                new String(tFlowElement.getId()),
                parentId,
                mapQNameList(callActivity.getIncoming()),
                mapQNameList(callActivity.getOutgoing()),
                loopCharacteristics,
                callActivity.getCalledElement().toString());
      }
      return activityFlowElement;
    } else if (tFlowElement instanceof TParallelGateway parallelGateway) {
      return new ParallelGateway(
          new String(parallelGateway.getId()),
          parentId,
          mapQNameList(parallelGateway.getIncoming()),
          mapQNameList(parallelGateway.getOutgoing()));
    } else if (tFlowElement instanceof TExclusiveGateway exclusiveGateway) {
      return new ExclusiveGateway(
          new String(exclusiveGateway.getId()),
          parentId,
          mapQNameList(exclusiveGateway.getIncoming()),
          mapQNameList(exclusiveGateway.getOutgoing()));
    } else if (tFlowElement instanceof TStartEvent startEvent) {
      return new StartEvent(
          new String(startEvent.getId()),
          parentId,
          mapQNameList(startEvent.getIncoming()),
          mapQNameList(startEvent.getOutgoing()),
          EventDefinitionMapper.map(startEvent.getEventDefinition(), parentId));
    } else if (tFlowElement instanceof TEndEvent endEvent) {
      return new EndEvent(
          new String(endEvent.getId()),
          parentId,
          mapQNameList(endEvent.getIncoming()),
          mapQNameList(endEvent.getOutgoing()));
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tFlowElement.getClass().getName());
  }

  private static Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(i -> new String(i.toString())).collect(Collectors.toSet());
  }
}
