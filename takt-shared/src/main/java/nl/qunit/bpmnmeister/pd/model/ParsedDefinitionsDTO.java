package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder
public class ParsedDefinitionsDTO extends DefinitionsTriggerDTO {
  private final DefinitionsKey definitionsKey;
  private final ProcessDTO rootProcess;
  private final Map<String, MessageDTO> messages;
  private final Map<String, EscalationDTO> escalations;
  private final Map<String, ErrorDTO> errors;

  @JsonCreator
  public ParsedDefinitionsDTO(
      @JsonProperty("definitionsKey") @Nonnull DefinitionsKey definitionsKey,
      @JsonProperty("elements") @Nonnull ProcessDTO rootProcess,
      @JsonProperty("messages") @Nonnull Map<String, MessageDTO> messages,
      @JsonProperty("escalations") @Nonnull Map<String, EscalationDTO> escalations,
      @JsonProperty("errors") @Nonnull Map<String, ErrorDTO> errors) {
    this.definitionsKey = definitionsKey;
    this.rootProcess = rootProcess;
    this.messages = messages;
    this.escalations = escalations;
    this.errors = errors;
  }
}
