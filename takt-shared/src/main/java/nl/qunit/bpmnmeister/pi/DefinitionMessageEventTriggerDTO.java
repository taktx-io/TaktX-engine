package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.MessageEventDTO;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

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
