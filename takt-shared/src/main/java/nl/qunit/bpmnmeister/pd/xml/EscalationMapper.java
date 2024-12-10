package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TEscalation;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EscalationDTO;

public interface EscalationMapper {

  EscalationDTO map(TEscalation tMessage);
}
