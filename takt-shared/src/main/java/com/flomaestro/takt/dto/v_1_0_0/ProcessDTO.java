package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
@NoArgsConstructor
public class ProcessDTO extends RootElementDTO {

  public static final ProcessDTO NONE =
      new ProcessDTO(Constants.NONE, Constants.NONE, FlowElementsDTO.EMPTY);

  @JsonProperty("fe")
  private FlowElementsDTO flowElements;

  public ProcessDTO(String id, String parentId, FlowElementsDTO flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }
}
