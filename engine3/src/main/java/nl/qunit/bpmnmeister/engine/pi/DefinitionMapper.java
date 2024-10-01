package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent2;
import nl.qunit.bpmnmeister.pd.model.CatchEvent2;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.Gateway2;
import nl.qunit.bpmnmeister.pd.model.Message2;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;
import nl.qunit.bpmnmeister.pd.model.WIthChildElements;
import nl.qunit.bpmnmeister.pd.model.WithMessageReference;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements2 getFlowElements(DefinitionsDTO definitionsDTO) {
    FlowElementsDTO flowElements = definitionsDTO.getRootProcess().getFlowElements();

    FlowElements2 flowElements1 = dtoMapper.getFlowElements(flowElements);

    setParentReferences(flowElements1.getElements(), null);
    setMessageReferences(flowElements1.getElements(), definitionsDTO.getMessages());
    setSequenceFlowReferences(flowElements1);
    return flowElements1;
  }

  private void setSequenceFlowReferences(FlowElements2 flowElements) {
    Map<String, SequenceFlow2> sequenceFlows = flowElements.getSequenceFlows();
    flowElements.getElements().values().stream()
        .filter(FlowNode2.class::isInstance)
        .map(FlowNode2.class::cast)
        .forEach(
            flowNode -> {
              flowNode
                  .getIncoming()
                  .forEach(id -> flowNode.getIncomingSequenceFlows().add(sequenceFlows.get(id)));
              flowNode
                  .getOutgoing()
                  .forEach(id -> flowNode.getOutGoingSequenceFlows().add(sequenceFlows.get(id)));
              if (flowNode instanceof Gateway2 gateway2) {
                gateway2.setDefaultSequenceFlow(sequenceFlows.get(gateway2.getDefaultFlow()));
              }
              if (flowNode instanceof WIthChildElements withChildElements) {
                setSequenceFlowReferences(withChildElements.getElements());
              }
              if (flowNode instanceof BoundaryEvent2 boundaryEvent) {
                boundaryEvent.setAttachedActivity(
                    flowElements.getFlowNode(boundaryEvent.getAttachedToRef()).get());
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
      Map<String, FlowElement2> elements, Map<String, MessageDTO> messages) {
    for (FlowElement2 flowElement : elements.values()) {
      if (flowElement instanceof WithMessageReference withMessageReference) {
        MessageDTO messageDTO = messages.get(withMessageReference.getMessageRef());
        withMessageReference.setReferencedMessage(
            new Message2(messageDTO.getId(), messageDTO.getName(), messageDTO.getCorrelationKey()));
      } else if (flowElement instanceof CatchEvent2 catchEvent) {
        catchEvent.getMessageventDefinitions().stream()
            .forEach(
                messageEventDefinition -> {
                  MessageDTO messageDTO = messages.get(messageEventDefinition.getMessageRef());
                  messageEventDefinition.setReferencedMessage(
                      new Message2(
                          messageDTO.getId(),
                          messageDTO.getName(),
                          messageDTO.getCorrelationKey()));
                });
      }
    }
  }

  private void setParentReferences(Map<String, FlowElement2> elements, FlowNode2 o) {
    for (FlowElement2 flowElement2 : elements.values()) {
      flowElement2.setParentElement(o);
      if (flowElement2 instanceof SubProcess2 subProcess) {
        setParentReferences(subProcess.getElements().getElements(), subProcess);
      }
    }
  }
}
