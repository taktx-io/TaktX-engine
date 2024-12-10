package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageDTO;

public interface MessageMapper {

  MessageDTO map(TMessage tMessage);
}
