package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import nl.qunit.bpmnmeister.engine.pd.model.Activity;
import nl.qunit.bpmnmeister.engine.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.engine.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.engine.pd.model.ErrorEvent;
import nl.qunit.bpmnmeister.engine.pd.model.EscalationEvent;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElement;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.FlowNode;
import nl.qunit.bpmnmeister.engine.pd.model.Gateway;
import nl.qunit.bpmnmeister.engine.pd.model.Message;
import nl.qunit.bpmnmeister.engine.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.engine.pd.model.SubProcess;
import nl.qunit.bpmnmeister.engine.pd.model.WIthChildElements;
import nl.qunit.bpmnmeister.engine.pd.model.WithErrorEventDefinitions;
import nl.qunit.bpmnmeister.engine.pd.model.WithEscalationEventDefinitions;
import nl.qunit.bpmnmeister.engine.pd.model.WithMessageReference;
import nl.qunit.bpmnmeister.pd.model.ErrorDTO;
import nl.qunit.bpmnmeister.pd.model.EscalationDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.ParsedDefinitionsDTO;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements getFlowElements(ParsedDefinitionsDTO definitionsDTO) {
    FlowElementsDTO flowElements = definitionsDTO.getRootProcess().getFlowElements();

    FlowElements flowElements1 = dtoMapper.getFlowElements(flowElements);
    setParentFlowEleemntReferences(flowElements1, null);
    setParentReferences(flowElements1.getElements(), null);
    setMessageReferences(flowElements1.getElements(), definitionsDTO.getMessages());
    setEscalationReferences(flowElements1.getElements(), definitionsDTO.getEscalations());
    setErrorReferences(flowElements1.getElements(), definitionsDTO.getErrors());
    setSequenceFlowReferences(flowElements1);
    return flowElements1;
  }

  private void setParentFlowEleemntReferences(
      FlowElements flowElements, FlowElements parentFlowElements) {
    flowElements.setParentElements(parentFlowElements);
    flowElements.getElements().values().stream()
        .filter(WIthChildElements.class::isInstance)
        .map(WIthChildElements.class::cast)
        .forEach(
            flowElement -> {
              FlowElements childElements = flowElement.getElements();
              setParentFlowEleemntReferences(childElements, flowElements);
            });
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
        withErrorEventDefinitions.getErrorEventDefinitions().stream()
            .forEach(
                errorEventDefinition -> {
                  ErrorDTO errorDTO = errors.get(errorEventDefinition.getErrorRef());
                  if (errorDTO != null) {
                    errorEventDefinition.setReferencedError(
                        new ErrorEvent(errorDTO.getName(), errorDTO.getCode()));
                  }
                });
      }
    }
  }

  private void setEscalationReferences(
      Map<String, FlowElement> elements, Map<String, EscalationDTO> escalations) {
    for (FlowElement flowElement : elements.values()) {
      if (flowElement instanceof WithEscalationEventDefinitions withEscalationEventDefinitions) {
        withEscalationEventDefinitions.getEscalationEventDefinitions().stream()
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
        catchEvent.getMessageventDefinitions().stream()
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
