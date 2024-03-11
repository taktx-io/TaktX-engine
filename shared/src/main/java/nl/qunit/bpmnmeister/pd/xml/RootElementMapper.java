package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.Process;
import nl.qunit.bpmnmeister.pd.model.RootElement;

public class RootElementMapper {
  private RootElementMapper() {}

  public static RootElement map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      BaseElementId id = new BaseElementId(tProcess.getId());
      return new Process(id, null, mapFlowElements(tProcess.getFlowElement()));
    }
    return null;
  }

  private static FlowElements mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<BaseElementId, BaseElement> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElement flowElement = FlowElementMapper.map(tFlowElement, null);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return new FlowElements(flowElements);
  }
}
