package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExternalTaskTriggerTimeoutDTO extends ProcessInstanceTriggerDTO {

  @JsonProperty("eiip")
  private List<UUID> elementInstanceIdPath;

  public ExternalTaskTriggerTimeoutDTO(UUID processInstanceKey, List<UUID> elementInstanceIdPath) {
    super(processInstanceKey, List.of(), VariablesDTO.empty());
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
