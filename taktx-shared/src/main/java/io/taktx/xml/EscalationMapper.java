package io.taktx.xml;

import io.taktx.bpmn.TEscalation;
import io.taktx.dto.EscalationDTO;

public interface EscalationMapper {

  EscalationDTO map(TEscalation tMessage);
}
