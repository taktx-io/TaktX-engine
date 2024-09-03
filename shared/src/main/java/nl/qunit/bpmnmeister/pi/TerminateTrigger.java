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

  private final UUID elementInstanceId;

  @JsonCreator
  public TerminateTrigger(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("elementId") List<String> elementIdPath,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId) {
    super(processInstanceKey, elementIdPath, VariablesDTO.empty());
    this.elementInstanceId = elementInstanceId;
  }
}
