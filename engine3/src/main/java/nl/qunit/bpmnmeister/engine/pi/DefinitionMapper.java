package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pd.model.WIthChildElements;
import nl.qunit.bpmnmeister.pd.model.WithMessageReference;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements getFlowElements(DefinitionsDTO definitionsDTO) {
    FlowElementsDTO flowElements = definitionsDTO.getRootProcess().getFlowElements();

    FlowElements flowElements1 = dtoMapper.getFlowElements(flowElements);

    setParentReferences(flowElements1.getElements(), null);
    setMessageReferences(flowElements1.getElements(), definitionsDTO.getMessages());
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
