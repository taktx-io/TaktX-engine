package io.taktx.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ExternalTaskDTO extends TaskDTO {

  private String workerDefinition;

  private String retries;

  private String implementation;

  private Map<String, String> headers;

  private List<ScheduleKeyDTO> scheduledKeys;

  protected ExternalTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping,
      String workerDefinition,
      String retries,
      String implementation,
      Map<String, String> headers) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.workerDefinition = workerDefinition;
    this.retries = retries;
    this.implementation = implementation;
    this.headers = headers;
  }
}
