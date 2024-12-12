package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TMessage;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;

public interface MessageMapper {

  MessageDTO map(TMessage tMessage);
}
