package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class BoundaryEventDTO extends CatchEventDTO {

  private final String attachedToRef;
  private final boolean cancelActivity;

  @JsonCreator
  public BoundaryEventDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("eventDefinitions") Set<EventDefinitionDTO> eventDefinitions,
      @Nonnull @JsonProperty("attachedToRef") String attachedToRef,
      @JsonProperty("cancelActivity") boolean cancelActivity,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
    this.attachedToRef = attachedToRef;
    this.cancelActivity = cancelActivity;
  }
}
