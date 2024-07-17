package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
public class CorrelationMessageSubscription extends MessageEvent {

  private final UUID rootInstanceKey;
  private final UUID processInstanceKey;
  private final String correlationKey;
  private final String elementId;
  private final UUID elementInstanceId1;

  @JsonCreator
  public CorrelationMessageSubscription(
      @Nonnull @JsonProperty("rootInstanceKey") UUID rootInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
    this.rootInstanceKey = rootInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementId = elementId;
    this.elementInstanceId1 = elementInstanceId;
  }
}
