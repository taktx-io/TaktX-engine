package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SubProcess extends Activity<SubProcessState> {

  private final FlowElements elements;

  @JsonCreator
  public SubProcess(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("elements") FlowElements elements) {

    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.elements = elements;
  }

  @JsonIgnore
  @Override
  public ProcessDefinition getAsSubProcessDefinition(ProcessDefinition parentProcessDefinition) {
    Integer version = parentProcessDefinition.getVersion();
    String parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
    Process process = new Process(parentProcessDefinitionId, parentProcessDefinitionId, elements);
    Definitions definitions =
        new Definitions(
            new DefinitionsKey(
                parentProcessDefinition
                        .getDefinitions()
                        .getDefinitionsKey()
                        .getProcessDefinitionId()
                    + "-"
                    + getId(),
                parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash()),
            process,
            Map.of());
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  @Override
  public String getAsSubProcessStartElementId() {
    List<StartEvent> startEvents = elements.getStartEvents();
    if (startEvents.size() != 1) {
      throw new IllegalStateException("SubProcess must have exactly one start event");
    }
    return startEvents.get(0).getId();
  }

  @Override
  public SubProcessState getInitialState(String inputFlowId, int passedCnt) {
    return new SubProcessState(FlowNodeStateEnum.READY, UUID.randomUUID(), passedCnt, 0, inputFlowId);
  }
}
