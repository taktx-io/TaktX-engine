package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public class CorrelationMessageSubscription extends MessageEvent {
  private final ProcessInstanceKey processInstanceKey;
  private final String correlationKey;
  private final String elementId;
  private final SubscribeAction subscribeAction;

  @JsonCreator
  public CorrelationMessageSubscription(
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("subscribeAction") SubscribeAction subscribeAction) {
    super(messageName);
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementId = elementId;
    this.subscribeAction = subscribeAction;
  }
}
