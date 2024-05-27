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
public class CancelCorrelationMessageSubscription extends MessageEvent {

  @JsonCreator
  public CancelCorrelationMessageSubscription(
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
  }
}
