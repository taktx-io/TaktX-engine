package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class CorrelationMessageSubscriptions {
  private Map<String, CorrelationMessageSubscription> instances;

  @JsonCreator
  public CorrelationMessageSubscriptions(
      @JsonProperty("instanceMap") Map<String, CorrelationMessageSubscription> instances) {
    this.instances = instances;
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions update(
      CorrelationMessageSubscription messageSubscription) {
    Map<String, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    newInstances.put(messageSubscription.getCorrelationKey(), messageSubscription);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions remove(String correlationKey) {
    Map<String, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    newInstances.remove(correlationKey);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions removeAll(Set<String> toRemove) {
    Map<String, CorrelationMessageSubscription> newInstances = new HashMap<>(instances);
    toRemove.forEach(newInstances::remove);
    return new CorrelationMessageSubscriptions(newInstances);
  }
}
