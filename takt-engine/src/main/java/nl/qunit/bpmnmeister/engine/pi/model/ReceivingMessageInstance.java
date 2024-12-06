package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

public interface ReceivingMessageInstance extends IFlowNodeInstance {
  void addMessageSubscriptionWithCorrelationKey(MessageEventKey messageEventKey, String string);

  Map<MessageEventKey, Set<String>> getMessageEventKeys();

  FlowNode getFlowNode();
}
