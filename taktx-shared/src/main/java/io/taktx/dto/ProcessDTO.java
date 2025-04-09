package io.taktx.dto;

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

  public static final ProcessDTO NONE = new ProcessDTO(null, null, null);

  @JsonProperty("f")
  private FlowElementsDTO flowElements;

  public ProcessDTO(String id, String parentId, FlowElementsDTO flowElements) {
    super(id, parentId);
    this.flowElements = flowElements;
  }
}
