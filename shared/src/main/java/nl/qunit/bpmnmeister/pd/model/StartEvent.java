package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.StartEventState;

@Getter
public class StartEvent extends CatchEvent<StartEventState> {

  @JsonCreator
  public StartEvent(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions) {
    super(id, parentId, incoming, outgoing, eventDefinitions);
  }

  @Override
  public StartEventState getInitialState() {
    return new StartEventState(UUID.randomUUID(), 0);
  }
}
