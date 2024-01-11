package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class EventDefinition extends RootElement {
  @JsonCreator
  protected EventDefinition(@JsonProperty("id") String id) {
    super(id);
  }
}
