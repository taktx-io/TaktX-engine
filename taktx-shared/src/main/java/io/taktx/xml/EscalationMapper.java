package io.taktx.xml;

import io.taktx.bpmn.TEscalation;
import io.taktx.dto.v_1_0_0.EscalationDTO;

public interface EscalationMapper {

  EscalationDTO map(TEscalation tMessage);
}
