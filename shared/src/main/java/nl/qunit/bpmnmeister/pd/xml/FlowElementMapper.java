package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TFlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowElement;

public interface FlowElementMapper extends Mapper {
  FlowElement map(TFlowElement tFlowElement, String parentId);
}
