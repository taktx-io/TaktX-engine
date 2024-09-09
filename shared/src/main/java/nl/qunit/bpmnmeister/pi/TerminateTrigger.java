package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class TerminateTrigger extends ProcessInstanceTrigger2 {

  private final List<UUID> elementInstanceIdPath;

  @JsonCreator
  public TerminateTrigger(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("elementInstanceIdPath") List<UUID> elementInstanceIdPath) {
    super(processInstanceKey, List.of(), VariablesDTO.empty());
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
