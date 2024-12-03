package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CancelCorrelationMessageSubscription extends MessageEvent {

  private String correlationKey;

  @JsonCreator
  public CancelCorrelationMessageSubscription(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("correlationKey") String correlationKey) {
    super(messageName);

    this.correlationKey = correlationKey;
  }
}
