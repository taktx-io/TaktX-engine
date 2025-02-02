package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ReceiveTask;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class ReceiveTaskInstance extends ActivityInstance<ReceiveTask>
    implements ReceivingMessageInstance {

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  private String correlationKey;

  public ReceiveTaskInstance(FlowNodeInstance<?> parentInstance, ReceiveTask flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
    messageEventKeys = new HashMap<>();
  }

  @Override
  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKeyDTO messageEventKey, String correlationKey) {
    messageEventKeys.computeIfAbsent(messageEventKey, k -> new HashSet<>()).add(correlationKey);
  }

  @Override
  public Map<MessageEventKeyDTO, Set<String>> getMessageEventKeys() {
    return messageEventKeys;
  }
}
