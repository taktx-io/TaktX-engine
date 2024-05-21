package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

public record MessageSubscription(
    @JsonProperty("key") ProcessDefinitionKey key,
    @JsonProperty("elementId") String elementId,
    @JsonProperty("messageName") String messageName)
    implements MessageEvent {
  @JsonCreator
  public MessageSubscription {
    // For @JsonCreator
  }

  @JsonIgnore
  public MessageEventKey getKey() {
    return new MessageEventKey(messageName);
  }
}
