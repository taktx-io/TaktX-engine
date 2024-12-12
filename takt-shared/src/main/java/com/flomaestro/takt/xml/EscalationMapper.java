package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TEscalation;
import com.flomaestro.takt.dto.v_1_0_0.EscalationDTO;

public interface EscalationMapper {

  EscalationDTO map(TEscalation tMessage);
}
