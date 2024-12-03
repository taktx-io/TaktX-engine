package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TEscalation;
import nl.qunit.bpmnmeister.pd.model.EscalationDTO;

public class GenericEscalationMapper implements EscalationMapper {

  @Override
  public EscalationDTO map(TEscalation tEscalation) {
    return new EscalationDTO(
        tEscalation.getId(), tEscalation.getName(), tEscalation.getEscalationCode());
  }
}
