package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TEscalation;
import com.flomaestro.takt.dto.v_1_0_0.EscalationDTO;

public class GenericEscalationMapper implements EscalationMapper {

  @Override
  public EscalationDTO map(TEscalation tEscalation) {
    return new EscalationDTO(
        tEscalation.getId(), tEscalation.getName(), tEscalation.getEscalationCode());
  }
}
