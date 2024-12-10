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
public class DefinitionMessageEventTriggerDTO extends MessageEventDTO {

  private VariablesDTO variables;

  @JsonCreator
  public DefinitionMessageEventTriggerDTO(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("variables") VariablesDTO variables) {
    super(messageName);
    this.variables = variables;
  }
}
