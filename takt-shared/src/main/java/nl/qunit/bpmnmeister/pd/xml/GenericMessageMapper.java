package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageDTO;

public class GenericMessageMapper implements MessageMapper {

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
