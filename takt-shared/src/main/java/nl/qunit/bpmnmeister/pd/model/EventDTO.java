package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class EventDTO extends FlowNodeDTO implements WithIoMappingDTO {

  private final InputOutputMappingDTO ioMapping;

  protected EventDTO(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.ioMapping = ioMapping;
  }
}
