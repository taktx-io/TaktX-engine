package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.FlowElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Process;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.RootElement;

@ApplicationScoped
public class RootElementMapper {
  @Inject FlowElementMapper flowElementMapper;

  public RootElement map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      String id = tProcess.getId();
      return Process.builder()
          .id(id)
          .flowElements(mapFlowElements(tProcess.getFlowElement()))
          .build();
    }
    return null;
  }

  private Map<String, FlowElement> mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<String, FlowElement> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElement flowElement = flowElementMapper.map(tFlowElement);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return flowElements;
  }
}
