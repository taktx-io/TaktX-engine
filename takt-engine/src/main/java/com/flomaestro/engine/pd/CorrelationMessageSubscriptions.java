package com.flomaestro.engine.pd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class CorrelationMessageSubscriptions {

  @JsonProperty("im")
  private Map<String, CorrelationMessageSubscriptionDTO> instances;

  public CorrelationMessageSubscriptions(Map<String, CorrelationMessageSubscriptionDTO> instances) {
    this.instances = instances;
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions update(
      CorrelationMessageSubscriptionDTO messageSubscription) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    newInstances.put(messageSubscription.getCorrelationKey(), messageSubscription);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions remove(String correlationKey) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    newInstances.remove(correlationKey);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions removeAll(Set<String> toRemove) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    toRemove.forEach(newInstances::remove);
    return new CorrelationMessageSubscriptions(newInstances);
  }
}
