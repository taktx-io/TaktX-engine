package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;

@NoArgsConstructor
@Getter
@Setter
public class DefinitionMessageSubscriptions {
  private Map<MessageEventKey, DefinitionMessageSubscription> definitions;

  @JsonCreator
  public DefinitionMessageSubscriptions(
      @JsonProperty("definitions")
          Map<MessageEventKey, DefinitionMessageSubscription> definitions) {
    this.definitions = definitions;
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions update(DefinitionMessageSubscription messageSubscription) {
    Map<MessageEventKey, DefinitionMessageSubscription> newDefinitions = new HashMap<>(definitions);
    newDefinitions.put(messageSubscription.toMessageEventKey(), messageSubscription);
    return new DefinitionMessageSubscriptions(newDefinitions);
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions remove(
      CancelDefinitionMessageSubscription messageSubscription) {
    Map<MessageEventKey, DefinitionMessageSubscription> newDefinitions = new HashMap<>(definitions);
    newDefinitions.remove(messageSubscription.toMessageEventKey());
    return new DefinitionMessageSubscriptions(newDefinitions);
  }
}
