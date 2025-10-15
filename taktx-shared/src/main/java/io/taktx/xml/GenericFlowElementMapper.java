/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TActivity;
import io.taktx.bpmn.TBaseElement;
import io.taktx.bpmn.TBoundaryEvent;
import io.taktx.bpmn.TCallActivity;
import io.taktx.bpmn.TCatchEvent;
import io.taktx.bpmn.TEndEvent;
import io.taktx.bpmn.TExclusiveGateway;
import io.taktx.bpmn.TFlowElement;
import io.taktx.bpmn.TGateway;
import io.taktx.bpmn.TInclusiveGateway;
import io.taktx.bpmn.TIntermediateCatchEvent;
import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.bpmn.TManualTask;
import io.taktx.bpmn.TParallelGateway;
import io.taktx.bpmn.TReceiveTask;
import io.taktx.bpmn.TScriptTask;
import io.taktx.bpmn.TSendTask;
import io.taktx.bpmn.TSequenceFlow;
import io.taktx.bpmn.TServiceTask;
import io.taktx.bpmn.TStartEvent;
import io.taktx.bpmn.TSubProcess;
import io.taktx.bpmn.TTask;
import io.taktx.bpmn.TThrowEvent;
import io.taktx.bpmn.TUserTask;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.CatchEventDTO;
import io.taktx.dto.EndEventDTO;
import io.taktx.dto.EventDefinitionDTO;
import io.taktx.dto.ExclusiveGatewayDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.GatewayDTO;
import io.taktx.dto.InclusiveGatewayDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IntermediateCatchEventDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.ParallelGatewayDTO;
import io.taktx.dto.SequenceFlowDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TaskDTO;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  private FlowElementDTO mapThrowEvent(String parentId, TThrowEvent throwEvent) {
    Set<EventDefinitionDTO> eventDefinitions =
        bpmnMapperFactory
            .createEventDefinitionMapper()
            .map(throwEvent.getEventDefinition(), parentId);

    if (throwEvent instanceof TEndEvent endEvent) {
      InputOutputMappingDTO ioMapping = bpmnMapperFactory.getIoMappingMapper().map(endEvent);
      if (hasMessageEventDefinition(eventDefinitions)) {
        return bpmnMapperFactory.createMessageEndEventMapper().map(endEvent, parentId, ioMapping);
      } else {
        return new EndEventDTO(
            endEvent.getId(),
            parentId,
            mapQNameList(endEvent.getIncoming()),
            mapQNameList(endEvent.getOutgoing()),
            ioMapping,
            eventDefinitions);
      }
    } else if (throwEvent instanceof TIntermediateThrowEvent intermediateThrowEvent) {
      if (hasMessageEventDefinition(eventDefinitions)) {
        InputOutputMappingDTO ioMapping =
            bpmnMapperFactory.getIoMappingMapper().map(intermediateThrowEvent);
        return bpmnMapperFactory
            .createMessageIntermediateThrowEventMapper()
            .map(intermediateThrowEvent, parentId, ioMapping);
      } else {
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
    }

    throw new IllegalStateException(
        "Unknown flow element type: " + throwEvent.getClass().getName());
  }

  private boolean hasMessageEventDefinition(Set<EventDefinitionDTO> eventDefinitions) {
    return eventDefinitions.stream()
        .anyMatch(eventDefinitionDTO -> eventDefinitionDTO instanceof MessageEventDefinitionDTO);
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
              : null);
    } else if (gateway instanceof TInclusiveGateway inclusiveGateway) {
      return new InclusiveGatewayDTO(
          inclusiveGateway.getId(),
          parentId,
          mapQNameList(inclusiveGateway.getIncoming()),
          mapQNameList(inclusiveGateway.getOutgoing()),
          (inclusiveGateway.getDefault() instanceof TSequenceFlow sequenceFlow)
              ? sequenceFlow.getId()
              : null);
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
          ioMapping,
          startEvent.isIsInterrupting());
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
      case TServiceTask serviceTask ->
          activityFlowElement =
              bpmnMapperFactory
                  .createServiceTaskMapper()
                  .map(serviceTask, parentId, loopCharacteristics, ioMapping);
      case TSendTask sendTask ->
          activityFlowElement =
              bpmnMapperFactory
                  .createSendTaskMapper()
                  .map(sendTask, parentId, loopCharacteristics, ioMapping);
      case TManualTask manualTask ->
          activityFlowElement =
              new TaskDTO(
                  manualTask.getId(),
                  parentId,
                  mapQNameList(manualTask.getIncoming()),
                  mapQNameList(manualTask.getOutgoing()),
                  loopCharacteristics,
                  ioMapping);
      case TReceiveTask receiveTask ->
          activityFlowElement =
              bpmnMapperFactory
                  .createReceiveTaskMapper()
                  .map(receiveTask, parentId, loopCharacteristics, ioMapping);
      case TUserTask userTask ->
          activityFlowElement =
              bpmnMapperFactory
                  .createUserTaskMapper()
                  .map(userTask, parentId, loopCharacteristics, ioMapping);
      case TScriptTask scriptTask ->
          activityFlowElement =
              bpmnMapperFactory
                  .createScriptTaskMapper()
                  .map(scriptTask, parentId, loopCharacteristics, ioMapping);
      case TTask task ->
          activityFlowElement =
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
                ioMapping,
                subProcess.isTriggeredByEvent());
      }
      case TCallActivity callActivity ->
          activityFlowElement =
              bpmnMapperFactory
                  .createCallActivityMapper()
                  .map(callActivity, parentId, loopCharacteristics, ioMapping);
      default ->
          throw new IllegalStateException(
              "Unknown activity type: " + activity.getClass().getName());
    }
    return activityFlowElement;
  }
}
