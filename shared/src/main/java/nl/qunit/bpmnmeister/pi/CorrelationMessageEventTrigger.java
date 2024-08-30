package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageEventTrigger extends MessageEvent {

  private final String correlationKey;
  private final VariablesDTO variables;

  @JsonCreator
  public CorrelationMessageEventTrigger(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("variables") VariablesDTO variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
