package io.taktx.xml;

import io.taktx.bpmn.TEscalation;
import io.taktx.dto.v_1_0_0.EscalationDTO;

public class GenericEscalationMapper implements EscalationMapper {

  @Override
  public EscalationDTO map(TEscalation tEscalation) {
    return new EscalationDTO(
        tEscalation.getId(), tEscalation.getName(), tEscalation.getEscalationCode());
  }
}
