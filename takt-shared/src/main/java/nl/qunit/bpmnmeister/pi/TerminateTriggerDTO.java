package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

@ToString
@Getter
public class TerminateTriggerDTO extends ProcessInstanceTriggerDTO {

  private final List<UUID> elementInstanceIdPath;

  @JsonCreator
  public TerminateTriggerDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("elementInstanceIdPath") List<UUID> elementInstanceIdPath) {
    super(processInstanceKey, List.of(), VariablesDTO.empty());
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
