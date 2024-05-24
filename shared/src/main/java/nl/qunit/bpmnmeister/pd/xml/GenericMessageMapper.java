package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Message;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public Message map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    return new Message(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
