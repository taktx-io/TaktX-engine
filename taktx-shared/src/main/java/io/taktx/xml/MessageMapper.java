package io.taktx.xml;

import io.taktx.bpmn.TMessage;
import io.taktx.dto.MessageDTO;

public interface MessageMapper {

  MessageDTO map(TMessage tMessage);
}
