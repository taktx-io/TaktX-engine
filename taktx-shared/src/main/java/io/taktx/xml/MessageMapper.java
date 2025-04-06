package io.taktx.xml;

import io.taktx.bpmn.TMessage;
import io.taktx.dto.v_1_0_0.MessageDTO;

public interface MessageMapper {

  MessageDTO map(TMessage tMessage);
}
