package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@EqualsAndHashCode(callSuper = true)
public class DefinitionMessageSubscription extends MessageEvent {
  private final ProcessDefinitionKey processDefinitionKey;
  private final String elementId;
  private final SubscribeAction subscribeAction;

  @JsonCreator
  public DefinitionMessageSubscription(
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("subscribeAction") SubscribeAction subscribeAction) {
    super(messageName);
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
    this.subscribeAction = subscribeAction;
  }
}
