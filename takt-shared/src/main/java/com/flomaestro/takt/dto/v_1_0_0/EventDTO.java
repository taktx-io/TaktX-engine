package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class EventDTO extends FlowNodeDTO implements WithIoMappingDTO {

  @JsonProperty("m")
  private InputOutputMappingDTO ioMapping;

  protected EventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.ioMapping = ioMapping;
  }
}
