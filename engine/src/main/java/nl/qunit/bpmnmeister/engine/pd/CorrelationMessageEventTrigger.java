package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageEventTrigger extends MessageEvent {

  private final String correlationKey;
  private final Variables variables;

  @JsonCreator
  public CorrelationMessageEventTrigger(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("variables") Variables variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
