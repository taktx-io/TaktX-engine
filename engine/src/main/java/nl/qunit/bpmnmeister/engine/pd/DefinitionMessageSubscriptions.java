package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class DefinitionMessageSubscriptions {
  private final Set<DefinitionMessageSubscription> definitions;

  @JsonCreator
  public DefinitionMessageSubscriptions(
      @JsonProperty("definitions") Set<DefinitionMessageSubscription> definitions) {
    this.definitions = definitions;
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions update(DefinitionMessageSubscription messageSubscription) {
    Set<DefinitionMessageSubscription> newDefinitions = new HashSet<>(definitions);
    if (messageSubscription.getSubscribeAction() == SubscribeAction.SUBSSCRIBE) {
      newDefinitions.add(messageSubscription);
    } else {
      DefinitionMessageSubscription toRemove =
          new DefinitionMessageSubscription(
              messageSubscription.getProcessDefinitionKey(),
              messageSubscription.getElementId(),
              messageSubscription.getMessageName(),
              SubscribeAction.SUBSSCRIBE);
      newDefinitions.remove(toRemove);
    }
    return new DefinitionMessageSubscriptions(newDefinitions);
  }
}
