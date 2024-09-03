package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements2 getFlowElements(FlowElementsDTO flowElements) {
    FlowElements2 flowElements1 = dtoMapper.getFlowElements(flowElements);

    setParentReferences(flowElements1.getElements(), null);

    return flowElements1;
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
