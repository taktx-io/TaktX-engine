package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ActivityDTO extends FlowNodeDTO implements WithIoMappingDTO {
  @JsonProperty("lc")
  private LoopCharacteristicsDTO loopCharacteristics;

  @JsonProperty("io")
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
