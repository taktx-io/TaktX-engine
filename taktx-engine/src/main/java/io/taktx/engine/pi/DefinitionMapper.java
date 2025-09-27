/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.ErrorDTO;
import io.taktx.dto.EscalationDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.MessageDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.ErrorEvent;
import io.taktx.engine.pd.model.EscalationEvent;
import io.taktx.engine.pd.model.FlowElement;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.WIthChildElements;
import io.taktx.engine.pd.model.WithErrorEventDefinitions;
import io.taktx.engine.pd.model.WithEscalationEventDefinitions;
import io.taktx.engine.pd.model.WithMessageReference;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements getFlowElements(ParsedDefinitionsDTO definitionsDTO) {
    FlowElementsDTO flowElements = definitionsDTO.getRootProcess().getFlowElements();

    FlowElements flowElements1 = dtoMapper.getFlowElements(flowElements);
    flowElements1.indexNodeIds();
    setParentReferences(flowElements1.getElements(), null);
    setMessageReferences(flowElements1.getElements(), definitionsDTO.getMessages());
    setEscalationReferences(flowElements1.getElements(), definitionsDTO.getEscalations());
    setErrorReferences(flowElements1.getElements(), definitionsDTO.getErrors());
    setSequenceFlowReferences(flowElements1);
    return flowElements1;
  }

  private void setSequenceFlowReferences(FlowElements flowElements) {
    Map<String, SequenceFlow> sequenceFlows = flowElements.getSequenceFlows();
    flowElements.getElements().values().stream()
        .filter(FlowNode.class::isInstance)
        .map(FlowNode.class::cast)
        .forEach(
            flowNode -> {
              flowNode
                  .getIncoming()
                  .forEach(id -> flowNode.getIncomingSequenceFlows().add(sequenceFlows.get(id)));
              flowNode
                  .getOutgoing()
                  .forEach(id -> flowNode.getOutGoingSequenceFlows().add(sequenceFlows.get(id)));
              if (flowNode instanceof Gateway gateway2) {
                gateway2.setDefaultSequenceFlow(sequenceFlows.get(gateway2.getDefaultFlow()));
              }
              if (flowNode instanceof WIthChildElements withChildElements) {
                setSequenceFlowReferences(withChildElements.getElements());
              }
              if (flowNode instanceof BoundaryEvent boundaryEvent) {
                Activity attachedActivity =
                    flowElements.getActivity(boundaryEvent.getAttachedToRef()).get();
                boundaryEvent.setAttachedActivity(attachedActivity);
                attachedActivity.addBoundaryEvent(boundaryEvent);
              }
            });

    sequenceFlows
        .values()
        .forEach(
            sequenceFlow -> {
              sequenceFlow.setSourceNode(flowElements.getFlowNode(sequenceFlow.getSource()).get());
              sequenceFlow.setTargetNode(flowElements.getFlowNode(sequenceFlow.getTarget()).get());
            });
  }

  private void setErrorReferences(Map<String, FlowElement> elements, Map<String, ErrorDTO> errors) {
    for (FlowElement flowElement : elements.values()) {
      if (flowElement instanceof WithErrorEventDefinitions withErrorEventDefinitions) {
        withErrorEventDefinitions.getErrorEventDefinition().stream()
            .forEach(
                errorEventDefinition -> {
                  ErrorDTO errorDTO = errors.get(errorEventDefinition.getErrorRef());
                  if (errorDTO != null) {
                    errorEventDefinition.setReferencedError(
                        new ErrorEvent(errorDTO.getName(), errorDTO.getCode()));
                  }
                });
      }
      if (flowElement instanceof WIthChildElements withChildElements) {
        setErrorReferences(withChildElements.getElements().getElements(), errors);
      }
    }
  }

  private void setEscalationReferences(
      Map<String, FlowElement> elements, Map<String, EscalationDTO> escalations) {
    for (FlowElement flowElement : elements.values()) {
      if (flowElement instanceof WithEscalationEventDefinitions withEscalationEventDefinitions) {
        withEscalationEventDefinitions.getEscalationEventDefinition().stream()
            .forEach(
                escalationEventDefinition -> {
                  EscalationDTO escalationDTO =
                      escalations.get(escalationEventDefinition.getEscalationRef());
                  if (escalationDTO != null) {
                    escalationEventDefinition.setReferencedEscalation(
                        new EscalationEvent(escalationDTO.getName(), escalationDTO.getCode()));
                  }
                });
      }
      if (flowElement instanceof WIthChildElements withChildElements) {
        setEscalationReferences(withChildElements.getElements().getElements(), escalations);
      }
    }
  }

  private void setMessageReferences(
      Map<String, FlowElement> elements, Map<String, MessageDTO> messages) {
    for (FlowElement flowElement : elements.values()) {
      if (flowElement instanceof WithMessageReference withMessageReference) {
        MessageDTO messageDTO = messages.get(withMessageReference.getMessageRef());
        withMessageReference.setReferencedMessage(
            new Message(messageDTO.getId(), messageDTO.getName(), messageDTO.getCorrelationKey()));
      } else if (flowElement instanceof CatchEvent catchEvent) {
        catchEvent.getMessageventDefinition().stream()
            .forEach(
                messageEventDefinition -> {
                  MessageDTO messageDTO = messages.get(messageEventDefinition.getMessageRef());
                  messageEventDefinition.setReferencedMessage(
                      new Message(
                          messageDTO.getId(),
                          messageDTO.getName(),
                          messageDTO.getCorrelationKey()));
                });
      }
      if (flowElement instanceof WIthChildElements withChildElements) {
        setMessageReferences(withChildElements.getElements().getElements(), messages);
      }
    }
  }

  private void setParentReferences(Map<String, FlowElement> elements, FlowNode o) {
    for (FlowElement flowElement2 : elements.values()) {
      flowElement2.setParentElement(o);
      if (flowElement2 instanceof SubProcess subProcess) {
        setParentReferences(subProcess.getElements().getElements(), subProcess);
      }
    }
  }
}
