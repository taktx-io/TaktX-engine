package io.taktx.dto;

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
  private DefinitionsKey definitionsKey;

  private ProcessDTO rootProcess;

  private Map<String, MessageDTO> messages;

  private Map<String, EscalationDTO> escalations;

  private Map<String, ErrorDTO> errors;
}
