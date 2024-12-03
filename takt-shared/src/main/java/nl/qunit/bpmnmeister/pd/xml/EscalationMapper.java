package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TEscalation;
import nl.qunit.bpmnmeister.pd.model.EscalationDTO;

public interface EscalationMapper {

  EscalationDTO map(TEscalation tMessage);
}
