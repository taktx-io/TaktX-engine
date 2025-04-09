package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TerminateTriggerDTO extends ProcessInstanceTriggerDTO {
  @JsonProperty("eiip")
  private List<Long> elementInstanceIdPath;

  public TerminateTriggerDTO(UUID processInstanceKey, List<Long> elementInstanceIdPath) {
    super(processInstanceKey, List.of(), VariablesDTO.empty());
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
