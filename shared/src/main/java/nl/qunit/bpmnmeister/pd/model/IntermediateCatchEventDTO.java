package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public class IntermediateCatchEventDTO extends CatchEventDTO {

  @JsonCreator
  public IntermediateCatchEventDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinitionDTO> eventDefinitions,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
  }
}
