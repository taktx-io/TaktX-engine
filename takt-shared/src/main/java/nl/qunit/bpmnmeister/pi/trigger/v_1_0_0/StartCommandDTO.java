package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.DefinitionsTriggerDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;

@Getter
@EqualsAndHashCode(callSuper = true)
public class StartCommandDTO extends DefinitionsTriggerDTO
    implements SchedulableMessageDTO<String> {

  private final UUID processInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final String elementId;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final String processDefinitionId;
  private final VariablesDTO variables;

  @JsonCreator
  public StartCommandDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("parentProcessInstanceKey") UUID parentProcessInstanceKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("parentElementIdPath") List<String> parentElementIdPath,
      @Nonnull @JsonProperty("parentElementInstancePath") List<UUID> parentElementInstancePath,
      @Nonnull @JsonProperty("processDefinitionId") String processDefinitionId,
      @Nonnull @JsonProperty("variables") VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.elementId = elementId;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionId = processDefinitionId;
    this.variables = variables;
  }

  @Override
  public String getRecordKey(UUID processInstanceKey) {
    return processDefinitionId;
  }
}
