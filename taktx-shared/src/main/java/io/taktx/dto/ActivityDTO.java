package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ActivityDTO extends FlowNodeDTO implements WithIoMappingDTO {
  private LoopCharacteristicsDTO loopCharacteristics;

  private InputOutputMappingDTO ioMapping;

  protected ActivityDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.loopCharacteristics = loopCharacteristics;
    this.ioMapping = ioMapping;
  }
}
