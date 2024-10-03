package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

@NoArgsConstructor
@Getter
@Setter
public class ReceiveTaskInstance extends ActivityInstance<ReceiveTask>
    implements ReceivingMessageInstance {
  private Map<MessageEventKey, Set<String>> messageEventKeys;

  private String correlationKey;

  public ReceiveTaskInstance(FLowNodeInstance<?> parentInstance, ReceiveTask flowNode) {
    super(parentInstance, flowNode);
    messageEventKeys = new HashMap<>();
  }

  @Override
  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKey messageEventKey, String correlationKey) {
    messageEventKeys.computeIfAbsent(messageEventKey, k -> new HashSet<>()).add(correlationKey);
  }

  @Override
  public Map<MessageEventKey, Set<String>> getMessageEventKeys() {
    return messageEventKeys;
  }
}
