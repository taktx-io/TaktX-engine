package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.TaskState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Task<S extends TaskState> extends Activity<TaskState> {
  @JsonCreator
  public Task(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
  }

  @JsonIgnore
  @Override
  public ProcessDefinition getAsSubProcessDefinition(ProcessDefinition parentProcessDefinition) {
    Map<String, FlowElement> elements = new HashMap<>();
    String sequenceFlowId = getId() + "-to-end";
    elements.put(getId(), withoutLoopCharacteristics(Set.of(sequenceFlowId)));
    String endEventId = getId() + "-end";
    elements.put(
        sequenceFlowId,
        new SequenceFlow(sequenceFlowId, getId(), getId(), endEventId, FlowCondition.NONE));
    elements.put(endEventId, new EndEvent(endEventId, getId(), Set.of(sequenceFlowId), Set.of()));

    // Wrap in Process element
    String parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
    Process process =
        new Process(
            parentProcessDefinitionId, parentProcessDefinitionId, new FlowElements(elements));

    DefinitionsKey subDefinitionsKey =
        new DefinitionsKey(
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                + "-"
                + getId(),
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash());
    Definitions definitions =
        new Definitions(
            subDefinitionsKey, process, parentProcessDefinition.getDefinitions().getMessages());

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  @Override
  public String getAsSubProcessStartElementId() {
    return getId();
  }

  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new Task<>(getId(), getId(), getIncoming(), outgoing, LoopCharacteristics.NONE);
  }

  @Override
  public TaskState getInitialState(String inputFlowId, int passedCnt) {
    return new TaskState(FlowNodeStateEnum.READY, UUID.randomUUID(), passedCnt, 0, inputFlowId);
  }
}
