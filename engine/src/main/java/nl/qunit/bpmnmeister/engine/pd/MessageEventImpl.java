package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
public class MessageEventImpl implements MessageEvent {

  private final String messageName;
  private final Variables variables;

  @JsonCreator
  public MessageEventImpl(
      @Nonnull @JsonProperty("messageName") String messageName,
      @JsonProperty("variables") Variables variables) {
    this.messageName = messageName;
    this.variables = variables;
  }
}
