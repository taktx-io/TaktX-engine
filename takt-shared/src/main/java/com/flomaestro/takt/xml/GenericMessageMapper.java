package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TMessage;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
