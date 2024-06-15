package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public class CorrelationMessageSubscription extends MessageEvent {

  @Nonnull
  private final ProcessInstanceKey rootInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final String correlationKey;
  private final String elementId;

  @JsonCreator
  public CorrelationMessageSubscription(
      @Nonnull @JsonProperty("rootInstanceKey") ProcessInstanceKey rootInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
    this.rootInstanceKey = rootInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementId = elementId;
  }
}
