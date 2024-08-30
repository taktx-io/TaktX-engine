package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

public class CorrelationMessageSubscriptions {
  private final Map<MessageEventKey, CorrelationMessageSubscription> instances;

  @JsonCreator
  public CorrelationMessageSubscriptions(
      @JsonProperty("instanceMap") Map<MessageEventKey, CorrelationMessageSubscription> instances) {
    this.instances = instances;
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions update(
      CorrelationMessageSubscription messageSubscription) {
    Map<MessageEventKey, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    newInstances.put(messageSubscription.getKey(), messageSubscription);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  public CorrelationMessageSubscriptions remove(MessageEventKey messageEventKey) {
    Map<MessageEventKey, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    newInstances.remove(messageEventKey);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  public CorrelationMessageSubscriptions removeAll(Set<MessageEventKey> toRemove) {
    Map<MessageEventKey, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    toRemove.forEach(newInstances::remove);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  public Map<MessageEventKey, CorrelationMessageSubscription> getInstances() {
    return instances;
  }
}
