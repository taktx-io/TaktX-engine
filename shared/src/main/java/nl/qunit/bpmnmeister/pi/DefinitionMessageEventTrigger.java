package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;

@Getter
@EqualsAndHashCode(callSuper = true)
public class DefinitionMessageEventTrigger extends MessageEvent {

  private final Variables variables;

  @JsonCreator
  public DefinitionMessageEventTrigger(
      @Nonnull @JsonProperty("messageName") String messageName,
      @Nonnull @JsonProperty("variables") Variables variables) {
    super(messageName);
    this.variables = variables;
  }
}
