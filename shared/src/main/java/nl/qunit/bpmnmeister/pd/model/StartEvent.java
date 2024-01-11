package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class StartEvent extends CatchEvent {

  @JsonCreator
  public StartEvent(
      @JsonProperty("id") String id,
      @JsonProperty("incoming") Set<String> incoming,
      @JsonProperty("outgoing") Set<String> outgoing,
      @JsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions) {
    super(eventDefinitions, id, incoming, outgoing);
  }
}
