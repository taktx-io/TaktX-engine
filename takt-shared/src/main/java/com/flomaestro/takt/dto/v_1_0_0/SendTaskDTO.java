package com.flomaestro.takt.dto.v_1_0_0;

import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SendTaskDTO extends ExternalTaskDTO {
  public SendTaskDTO(
      String id,
      String parentId,
      String workerDefinition,
      String retries,
      Set<String> incoming,
      Set<String> outgoing,
      String implementation,
      LoopCharacteristicsDTO loopCharacteristics,
      Map<String, String> headers,
      InputOutputMappingDTO ioMapping) {
    super(
        id,
        parentId,
        incoming,
        outgoing,
        loopCharacteristics,
        ioMapping,
        workerDefinition,
        retries,
        implementation,
        headers);
  }
}
