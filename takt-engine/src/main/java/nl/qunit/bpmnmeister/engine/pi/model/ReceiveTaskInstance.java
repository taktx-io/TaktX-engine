package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.pi.state.MessageEventKeyDTO;

@NoArgsConstructor
@Getter
@Setter
public class ReceiveTaskInstance extends ActivityInstance<ReceiveTask>
    implements ReceivingMessageInstance {

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  private String correlationKey;

  public ReceiveTaskInstance(FLowNodeInstance<?> parentInstance, ReceiveTask flowNode) {
    super(parentInstance, flowNode);
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
