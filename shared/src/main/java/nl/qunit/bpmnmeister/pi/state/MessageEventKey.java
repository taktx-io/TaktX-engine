package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageEventKey(@JsonProperty("messageName") String messageName) {
  @JsonCreator
  public MessageEventKey {
    // For @JsonCreator
  }
}
