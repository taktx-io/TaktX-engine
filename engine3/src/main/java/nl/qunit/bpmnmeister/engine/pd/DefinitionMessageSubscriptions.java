package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

public class DefinitionMessageSubscriptions {
  private final Map<MessageEventKey, DefinitionMessageSubscription> definitions;

  @JsonCreator
  public DefinitionMessageSubscriptions(
      @JsonProperty("definitions")
          Map<MessageEventKey, DefinitionMessageSubscription> definitions) {
    this.definitions = definitions;
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions update(DefinitionMessageSubscription messageSubscription) {
    Map<MessageEventKey, DefinitionMessageSubscription> newDefinitions = new HashMap<>(definitions);
    newDefinitions.put(messageSubscription.getKey(), messageSubscription);
    return new DefinitionMessageSubscriptions(newDefinitions);
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions remove(
      CancelDefinitionMessageSubscription messageSubscription) {
    Map<MessageEventKey, DefinitionMessageSubscription> newDefinitions = new HashMap<>(definitions);
    newDefinitions.remove(messageSubscription.getKey());
    return new DefinitionMessageSubscriptions(newDefinitions);
  }

  public Map<MessageEventKey, DefinitionMessageSubscription> getDefinitions() {
    return definitions;
  }
}
