package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ExternalTaskDTO extends TaskDTO {

  @JsonProperty("wd")
  private String workerDefinition;

  @JsonProperty("rtr")
  private String retries;

  @JsonProperty("imp")
  private String implementation;

  @JsonProperty("hdr")
  private Map<String, String> headers;

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
