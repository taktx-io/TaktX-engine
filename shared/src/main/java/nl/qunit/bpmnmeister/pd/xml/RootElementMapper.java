package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.Process;
import nl.qunit.bpmnmeister.pd.model.RootElement;

public class RootElementMapper {
  public static RootElement map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      String id = tProcess.getId();
      return new Process(id, mapFlowElements(tProcess.getFlowElement()));
    }
    return null;
  }

  private static Map<String, FlowElement> mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<String, FlowElement> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElement flowElement = FlowElementMapper.map(tFlowElement);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return flowElements;
  }
}
