package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CorrelationMessageSubscriptions {
  private final Set<CorrelationMessageSubscription> instances;

  @JsonCreator
  public CorrelationMessageSubscriptions(
      @JsonProperty("instanceMap") Set<CorrelationMessageSubscription> instances) {
    this.instances = instances;
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions update(
      CorrelationMessageSubscription messageSubscription) {
    Set<CorrelationMessageSubscription> newInstances = new HashSet<>(instances);
    if (messageSubscription.getSubscribeAction() == SubscribeAction.SUBSSCRIBE) {
      newInstances.add(messageSubscription);
    } else {
      CorrelationMessageSubscription toRemove =
          new CorrelationMessageSubscription(
              messageSubscription.getProcessInstanceKey(),
              messageSubscription.getCorrelationKey(),
              messageSubscription.getElementId(),
              messageSubscription.getMessageName(),
              SubscribeAction.SUBSSCRIBE);
      newInstances.remove(toRemove);
    }
    return new CorrelationMessageSubscriptions(newInstances);
  }

  public CorrelationMessageSubscriptions remove(CorrelationMessageSubscription subscription) {
    Set<CorrelationMessageSubscription> newInstances = new HashSet<>(instances);
    newInstances.remove(subscription);
    return new CorrelationMessageSubscriptions(newInstances);
  }
}
