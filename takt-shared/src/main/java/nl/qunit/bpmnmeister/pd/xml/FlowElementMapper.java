package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElementDTO;

public interface FlowElementMapper extends Mapper {
  FlowElementDTO map(TFlowElement tFlowElement, String parentId);
}
