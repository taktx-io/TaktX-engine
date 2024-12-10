package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowElementDTO;

public interface FlowElementMapper extends Mapper {
  FlowElementDTO map(TFlowElement tFlowElement, String parentId);
}
