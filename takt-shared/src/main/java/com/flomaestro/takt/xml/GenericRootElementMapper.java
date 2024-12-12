package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TFlowElement;
import com.flomaestro.bpmn.TProcess;
import com.flomaestro.bpmn.TRootElement;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDTO;
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
      return new ProcessDTO(id, Constants.NONE, mapFlowElements(tProcess.getFlowElement()));
    }
    return ProcessDTO.NONE;
  }

  private FlowElementsDTO mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<String, FlowElementDTO> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElementDTO flowElement =
          bpmnMapperFactory.createFlowElementMapper().map(tFlowElement, Constants.NONE);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return new FlowElementsDTO(flowElements);
  }
}
