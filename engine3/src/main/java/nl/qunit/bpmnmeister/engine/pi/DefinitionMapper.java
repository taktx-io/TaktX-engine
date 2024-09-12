package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.Message2;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask2;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;

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
    return flowElements1;
  }

  private void setMessageReferences(
      Map<String, FlowElement2> elements, Map<String, MessageDTO> messages) {
    for (FlowElement2 flowElement2 : elements.values()) {
      if (flowElement2 instanceof ReceiveTask2 receiveTask) {
        MessageDTO messageDTO = messages.get(receiveTask.getMessageRef());
        receiveTask.setMessage(
            new Message2(messageDTO.getId(), messageDTO.getName(), messageDTO.getCorrelationKey()));
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
