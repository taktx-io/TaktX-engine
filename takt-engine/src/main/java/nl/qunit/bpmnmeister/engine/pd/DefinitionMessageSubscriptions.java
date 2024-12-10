package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscriptionDTO;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscriptionDTO;
import nl.qunit.bpmnmeister.pi.state.MessageEventKeyDTO;

@NoArgsConstructor
@Getter
@Setter
public class DefinitionMessageSubscriptions {

  private Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions;

  @JsonCreator
  public DefinitionMessageSubscriptions(
      @JsonProperty("definitions")
      Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions) {
    this.definitions = definitions;
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions update(
      DefinitionMessageSubscriptionDTO messageSubscription) {
    Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> newDefinitions =
        new HashMap<>(definitions);
    newDefinitions.put(messageSubscription.toMessageEventKey(), messageSubscription);
    return new DefinitionMessageSubscriptions(newDefinitions);
  }

  @JsonIgnore
  public DefinitionMessageSubscriptions remove(
      CancelDefinitionMessageSubscriptionDTO messageSubscription) {
    Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> newDefinitions =
        new HashMap<>(definitions);
    newDefinitions.remove(messageSubscription.toMessageEventKey());
    return new DefinitionMessageSubscriptions(newDefinitions);
  }
}
