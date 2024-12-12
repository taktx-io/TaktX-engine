package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import java.util.Map;
import java.util.Set;

public interface ReceivingMessageInstance extends IFlowNodeInstance {

  void addMessageSubscriptionWithCorrelationKey(MessageEventKeyDTO messageEventKey, String string);

  Map<MessageEventKeyDTO, Set<String>> getMessageEventKeys();

  FlowNode getFlowNode();
}
