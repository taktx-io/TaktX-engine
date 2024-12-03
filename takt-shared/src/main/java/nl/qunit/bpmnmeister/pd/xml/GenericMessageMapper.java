package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
