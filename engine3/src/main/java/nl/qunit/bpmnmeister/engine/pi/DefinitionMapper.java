package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;

@ApplicationScoped
public class DefinitionMapper {
  private final DtoMapper dtoMapper;

  public DefinitionMapper(DtoMapper dtoMapper) {
    this.dtoMapper = dtoMapper;
  }

  public FlowElements2 getFlowElements(FlowElementsDTO flowElements) {
    FlowElements2 result = new FlowElements2();
    flowElements
        .getElements()
        .values()
        .forEach(
            flowElementDTO -> result
                .getElements()
                .put(flowElementDTO.getId(), (FlowElement2) dtoMapper.map(flowElementDTO)));

    return result;
  }
}
