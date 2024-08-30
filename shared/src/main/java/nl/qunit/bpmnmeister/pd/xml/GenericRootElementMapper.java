package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElementDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.Process;

public class GenericRootElementMapper implements RootElementMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericRootElementMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public Process map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      String id = tProcess.getId();
      return new Process(id, Constants.NONE, mapFlowElements(tProcess.getFlowElement()));
    }
    return Process.NONE;
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
