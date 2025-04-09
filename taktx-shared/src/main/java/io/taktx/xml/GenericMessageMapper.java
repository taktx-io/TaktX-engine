package io.taktx.xml;

import io.taktx.bpmn.TMessage;
import io.taktx.dto.MessageDTO;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = null;
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
