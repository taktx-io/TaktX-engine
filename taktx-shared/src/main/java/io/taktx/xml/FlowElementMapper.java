package io.taktx.xml;

import io.taktx.bpmn.TFlowElement;
import io.taktx.dto.v_1_0_0.FlowElementDTO;

public interface FlowElementMapper extends Mapper {
  FlowElementDTO map(TFlowElement tFlowElement, String parentId);
}
