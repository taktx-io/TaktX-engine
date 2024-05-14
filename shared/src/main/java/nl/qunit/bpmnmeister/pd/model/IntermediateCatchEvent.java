package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventStateEnum;

@Getter
public class IntermediateCatchEvent extends CatchEvent<IntermediateCatchEventState> {

  @JsonCreator
  public IntermediateCatchEvent(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions) {
    super(id, parentId, incoming, outgoing, eventDefinitions);
  }

  @Override
  public IntermediateCatchEventState getInitialState() {
    return new IntermediateCatchEventState(UUID.randomUUID(), 0, IntermediateCatchEventStateEnum.READY, Set.of());
  }
}
