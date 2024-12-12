package com.flomaestro.engine.pd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flomaestro.takt.dto.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class DefinitionMessageSubscriptions {
  @JsonProperty("def")
  private Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions;

  public DefinitionMessageSubscriptions(
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
