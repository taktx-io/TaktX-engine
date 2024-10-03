package nl.qunit.bpmnmeister.pi.instances;

import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

public interface ReceivingMessageInstance extends IFlowNodeInstance {
  void addMessageSubscriptionWithCorrelationKey(MessageEventKey messageEventKey, String string);

  Map<MessageEventKey, Set<String>> getMessageEventKeys();

  FlowNode getFlowNode();
}
