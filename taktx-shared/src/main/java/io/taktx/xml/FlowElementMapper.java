package io.taktx.xml;

import io.taktx.bpmn.TFlowElement;
import io.taktx.dto.FlowElementDTO;

public interface FlowElementMapper extends Mapper {
  FlowElementDTO map(TFlowElement tFlowElement, String parentId);
}
