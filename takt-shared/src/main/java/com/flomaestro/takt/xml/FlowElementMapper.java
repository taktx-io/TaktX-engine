package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TFlowElement;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementDTO;

public interface FlowElementMapper extends Mapper {
  FlowElementDTO map(TFlowElement tFlowElement, String parentId);
}
