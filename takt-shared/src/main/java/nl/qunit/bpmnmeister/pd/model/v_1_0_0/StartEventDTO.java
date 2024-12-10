package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public class StartEventDTO extends CatchEventDTO {

  @JsonCreator
  public StartEventDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinitionDTO> eventDefinitions,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
  }
}
