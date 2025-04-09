package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SubProcessDTO extends ActivityDTO {

  @JsonProperty("el")
  private FlowElementsDTO elements;

  public SubProcessDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      FlowElementsDTO elements,
      InputOutputMappingDTO ioMapping) {

    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.elements = elements;
  }
}
