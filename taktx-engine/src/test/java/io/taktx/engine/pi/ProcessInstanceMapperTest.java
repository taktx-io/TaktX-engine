package io.taktx.engine.pi;

import static org.junit.jupiter.api.Assertions.*;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.SubProcessInstance;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ProcessInstanceMapperTest {

  @Test
  void testFlowNodeInstances_fromDTO_NotNull() {
    ProcessInstanceMapper mapper = Mappers.getMapper(ProcessInstanceMapper.class);
    FlowElements flowElements = new FlowElements();
    FlowElements subProcessElements = new FlowElements();
    SubProcess subProcess =
        SubProcess.builder().id("subProcess").elements(subProcessElements).build();

    flowElements.addFlowElement(subProcess);
    flowElements.indexNodeIds();

    SubProcessInstanceDTO source = new SubProcessInstanceDTO();

    source.setState(ExecutionState.ACTIVE);
    ScopeDTO scope = new ScopeDTO();
    scope.setState(ExecutionState.ACTIVE);
    source.setScope(scope);

    SubProcessInstance mapped = mapper.map(source, flowElements);

    assertNotNull(mapped.getScope().getFlowNodeInstances());
  }
}
