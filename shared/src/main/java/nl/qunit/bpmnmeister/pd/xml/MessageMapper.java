package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;

public interface MessageMapper {

  MessageDTO map(TMessage tMessage);
}
