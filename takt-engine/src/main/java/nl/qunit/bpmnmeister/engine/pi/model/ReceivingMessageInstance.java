package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventKeyDTO;

public interface ReceivingMessageInstance extends IFlowNodeInstance {

  void addMessageSubscriptionWithCorrelationKey(MessageEventKeyDTO messageEventKey, String string);

  Map<MessageEventKeyDTO, Set<String>> getMessageEventKeys();

  FlowNode getFlowNode();
}
