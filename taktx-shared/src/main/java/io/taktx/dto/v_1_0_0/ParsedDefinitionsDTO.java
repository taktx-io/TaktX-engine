package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDefinitionsDTO extends DefinitionsTriggerDTO {
  @JsonProperty("dk")
  private DefinitionsKey definitionsKey;

  @JsonProperty("rp")
  private ProcessDTO rootProcess;

  @JsonProperty("msg")
  private Map<String, MessageDTO> messages;

  @JsonProperty("esc")
  private Map<String, EscalationDTO> escalations;

  @JsonProperty("err")
  private Map<String, ErrorDTO> errors;
}
