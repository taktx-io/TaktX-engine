package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class ActivityDTO extends FlowNodeDTO implements WithIoMappingDTO {
  private final LoopCharacteristicsDTO loopCharacteristics;
  private final InputOutputMappingDTO ioMapping;

  protected ActivityDTO(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull LoopCharacteristicsDTO loopCharacteristics,
      @Nonnull InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.loopCharacteristics = loopCharacteristics;
    this.ioMapping = ioMapping;
  }
}
