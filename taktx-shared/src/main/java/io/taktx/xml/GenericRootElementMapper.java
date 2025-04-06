package io.taktx.xml;

import io.taktx.bpmn.TFlowElement;
import io.taktx.bpmn.TProcess;
import io.taktx.bpmn.TRootElement;
import io.taktx.dto.v_1_0_0.FlowElementDTO;
import io.taktx.dto.v_1_0_0.FlowElementsDTO;
import io.taktx.dto.v_1_0_0.ProcessDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericRootElementMapper implements RootElementMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericRootElementMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public ProcessDTO map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      String id = tProcess.getId();
      return new ProcessDTO(id, null, mapFlowElements(tProcess.getFlowElement()));
    }
    return ProcessDTO.NONE;
  }

  private FlowElementsDTO mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<String, FlowElementDTO> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElementDTO flowElement =
          bpmnMapperFactory.createFlowElementMapper().map(tFlowElement, null);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return new FlowElementsDTO(flowElements);
  }
}
