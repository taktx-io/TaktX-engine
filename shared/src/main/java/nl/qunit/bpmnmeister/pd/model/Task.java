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
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("ioMapping") InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
  }

  @JsonIgnore
  @Override
  public ProcessDefinition getAsSubProcessDefinition(ProcessDefinition parentProcessDefinition) {
    Map<String, FlowElement> elements = new HashMap<>();
    elements.put(getId(), withoutLoopCharacteristics());

    // Wrap in Process element
    String parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
    Process process =
        new Process(
            parentProcessDefinitionId, parentProcessDefinitionId, new FlowElements(elements));

    DefinitionsKey subDefinitionsKey =
        new DefinitionsKey(
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                + "/"
                + getId(),
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash());
    Definitions definitions =
        new Definitions(
            subDefinitionsKey, process, parentProcessDefinition.getDefinitions().getMessages());

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  protected FlowElement withoutLoopCharacteristics() {
    return new Task<>(getId(), getId(), getIncoming(), getOutgoing(), LoopCharacteristics.NONE, getIoMapping());
  }

  @Override
  public TaskState getInitialState(UUID parentElementInstanceId, String elementId, String inputFlowId, int passedCnt) {
    return new TaskState(FlowNodeStateEnum.READY, parentElementInstanceId, UUID.randomUUID(), elementId, passedCnt, 0, inputFlowId);
  }
}
