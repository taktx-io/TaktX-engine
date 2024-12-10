package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageEventTriggerDTO extends MessageEventDTO {

  private String correlationKey;
  private VariablesDTO variables;

  @JsonCreator
  public CorrelationMessageEventTriggerDTO(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("variables") VariablesDTO variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
