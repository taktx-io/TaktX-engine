package nl.qunit.bpmnmeister.pd.xml;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.bpmn.TActivity;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.bpmn.TBoundaryEvent;
import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.bpmn.TCatchEvent;
import nl.qunit.bpmnmeister.bpmn.TEndEvent;
import nl.qunit.bpmnmeister.bpmn.TExclusiveGateway;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TGateway;
import nl.qunit.bpmnmeister.bpmn.TInclusiveGateway;
import nl.qunit.bpmnmeister.bpmn.TIntermediateCatchEvent;
import nl.qunit.bpmnmeister.bpmn.TIntermediateThrowEvent;
import nl.qunit.bpmnmeister.bpmn.TParallelGateway;
import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.bpmn.TSequenceFlow;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.bpmn.TStartEvent;
import nl.qunit.bpmnmeister.bpmn.TSubProcess;
import nl.qunit.bpmnmeister.bpmn.TTask;
import nl.qunit.bpmnmeister.bpmn.TThrowEvent;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.BoundaryEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.CatchEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EndEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ExclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowConditionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowElementDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.GatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.IntermediateCatchEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.IntermediateThrowEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParallelGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SequenceFlowDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.StartEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SubProcessDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TaskDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ThrowEventDTO;

public class GenericFlowElementMapper implements FlowElementMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericFlowElementMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public FlowElementDTO map(TFlowElement tFlowElement, String parentId) {
    if (tFlowElement instanceof TSequenceFlow tSequenceFlow) {
      return mapSequenceFlow(parentId, tSequenceFlow);
    } else if (tFlowElement instanceof TActivity activity) {
      return mapActivity(activity, parentId);
    } else if (tFlowElement instanceof TGateway gateway) {
      return mapGateway(gateway, parentId);
    } else if (tFlowElement instanceof TCatchEvent tCatchEvent) {
      return mapCatchEvent(parentId, tCatchEvent);
    } else if (tFlowElement instanceof TThrowEvent throwEvent) {
      return mapThrowEvent(parentId, throwEvent);
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tFlowElement.getClass().getName());
  }

  private ThrowEventDTO mapThrowEvent(String parentId, TThrowEvent throwEvent) {
    Set<EventDefinitionDTO> eventDefinitions =
        bpmnMapperFactory
            .createEventDefinitionMapper()
            .map(throwEvent.getEventDefinition(), parentId);

    if (throwEvent instanceof TEndEvent endEvent) {
      InputOutputMappingDTO ioMapping = bpmnMapperFactory.getIoMappingMapper().map(endEvent);
      return new EndEventDTO(
          endEvent.getId(),
          parentId,
          mapQNameList(endEvent.getIncoming()),
          mapQNameList(endEvent.getOutgoing()),
          ioMapping,
          eventDefinitions);
    } else if (throwEvent instanceof TIntermediateThrowEvent intermediateThrowEvent) {
      InputOutputMappingDTO ioMapping =
          bpmnMapperFactory.getIoMappingMapper().map(intermediateThrowEvent);
      return new IntermediateThrowEventDTO(
          intermediateThrowEvent.getId(),
          parentId,
          mapQNameList(intermediateThrowEvent.getIncoming()),
          mapQNameList(intermediateThrowEvent.getOutgoing()),
          ioMapping,
          eventDefinitions);
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + throwEvent.getClass().getName());
  }

  private GatewayDTO mapGateway(TGateway gateway, String parentId) {
    if (gateway instanceof TParallelGateway parallelGateway) {
      return new ParallelGatewayDTO(
          parallelGateway.getId(),
          parentId,
          mapQNameList(parallelGateway.getIncoming()),
          mapQNameList(parallelGateway.getOutgoing()));
    } else if (gateway instanceof TExclusiveGateway exclusiveGateway) {
      return new ExclusiveGatewayDTO(
          exclusiveGateway.getId(),
          parentId,
          mapQNameList(exclusiveGateway.getIncoming()),
          mapQNameList(exclusiveGateway.getOutgoing()),
          (exclusiveGateway.getDefault() instanceof TSequenceFlow sequenceFlow)
              ? sequenceFlow.getId()
              : Constants.NONE);
    } else if (gateway instanceof TInclusiveGateway inclusiveGateway) {
      return new InclusiveGatewayDTO(
          inclusiveGateway.getId(),
          parentId,
          mapQNameList(inclusiveGateway.getIncoming()),
          mapQNameList(inclusiveGateway.getOutgoing()),
          (inclusiveGateway.getDefault() instanceof TSequenceFlow sequenceFlow)
              ? sequenceFlow.getId()
              : Constants.NONE);
    }

    throw new IllegalStateException("Unknown flow element type: " + gateway.getClass().getName());
  }

  private CatchEventDTO mapCatchEvent(String parentId, TCatchEvent tCatchEvent) {
    InputOutputMappingDTO ioMapping = bpmnMapperFactory.getIoMappingMapper().map(tCatchEvent);
    if (tCatchEvent instanceof TStartEvent startEvent) {
      return new StartEventDTO(
          startEvent.getId(),
          parentId,
          mapQNameList(startEvent.getIncoming()),
          mapQNameList(startEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(startEvent.getEventDefinition(), parentId),
          ioMapping);
    } else if (tCatchEvent instanceof TBoundaryEvent boundaryEvent) {
      return new BoundaryEventDTO(
          boundaryEvent.getId(),
          parentId,
          mapQNameList(boundaryEvent.getIncoming()),
          mapQNameList(boundaryEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(boundaryEvent.getEventDefinition(), parentId),
          boundaryEvent.getAttachedToRef().toString(),
          boundaryEvent.isCancelActivity(),
          ioMapping);
    } else if (tCatchEvent instanceof TIntermediateCatchEvent intermediateCatchEvent) {
      return new IntermediateCatchEventDTO(
          intermediateCatchEvent.getId(),
          parentId,
          mapQNameList(intermediateCatchEvent.getIncoming()),
          mapQNameList(intermediateCatchEvent.getOutgoing()),
          bpmnMapperFactory
              .createEventDefinitionMapper()
              .map(intermediateCatchEvent.getEventDefinition(), parentId),
          ioMapping);
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + tCatchEvent.getClass().getName());
  }

  private SequenceFlowDTO mapSequenceFlow(String parentId, TSequenceFlow tSequenceFlow) {
    return new SequenceFlowDTO(
        tSequenceFlow.getId(),
        parentId,
        ((TBaseElement) tSequenceFlow.getSourceRef()).getId(),
        ((TBaseElement) tSequenceFlow.getTargetRef()).getId(),
        tSequenceFlow.getConditionExpression() != null
            ? new FlowConditionDTO(
                tSequenceFlow.getConditionExpression().getContent().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("")))
            : FlowConditionDTO.NONE);
  }

  private FlowElementDTO mapActivity(TActivity activity, String parentId) {
    InputOutputMappingDTO ioMapping = bpmnMapperFactory.getIoMappingMapper().map(activity);

    LoopCharacteristicsDTO loopCharacteristics =
        bpmnMapperFactory.createLoopCharacteristicsMapper().map(activity.getLoopCharacteristics());
    FlowElementDTO activityFlowElement = null;
    switch (activity) {
      case TServiceTask serviceTask -> activityFlowElement =
          bpmnMapperFactory
              .createServiceTaskMapper()
              .map(serviceTask, parentId, loopCharacteristics, ioMapping);
      case TSendTask sendTask -> activityFlowElement =
          bpmnMapperFactory
              .createSendTaskMapper()
              .map(sendTask, parentId, loopCharacteristics, ioMapping);
      case TReceiveTask receiveTask -> activityFlowElement =
          bpmnMapperFactory
              .createReceiveTaskMapper()
              .map(receiveTask, parentId, loopCharacteristics, ioMapping);
      case TTask task -> activityFlowElement =
          new TaskDTO(
              task.getId(),
              parentId,
              mapQNameList(task.getIncoming()),
              mapQNameList(task.getOutgoing()),
              loopCharacteristics,
              ioMapping);
      case TSubProcess subProcess -> {
        Map<String, FlowElementDTO> elements =
            subProcess.getFlowElement().stream()
                .map(
                    flowElement ->
                        bpmnMapperFactory
                            .createFlowElementMapper()
                            .map(flowElement.getValue(), activity.getId()))
                .collect(Collectors.toMap(FlowElementDTO::getId, Function.identity()));

        activityFlowElement =
            new SubProcessDTO(
                activity.getId(),
                parentId,
                mapQNameList(subProcess.getIncoming()),
                mapQNameList(subProcess.getOutgoing()),
                loopCharacteristics,
                new FlowElementsDTO(elements),
                ioMapping);
      }
      case TCallActivity callActivity -> activityFlowElement =
          bpmnMapperFactory
              .createCallActivityMapper()
              .map(callActivity, parentId, loopCharacteristics, ioMapping);
      default -> throw new IllegalStateException(
          "Unknown activity type: " + activity.getClass().getName());
    }
    return activityFlowElement;
  }
}
