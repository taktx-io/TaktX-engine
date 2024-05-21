package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageEventKey(@JsonProperty("messageName") String messageName) {
  @JsonCreator
  public MessageEventKey {
    // For @JsonCreator
  }
}
