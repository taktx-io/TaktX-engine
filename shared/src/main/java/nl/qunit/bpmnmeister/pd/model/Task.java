package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public class Task extends Activity {
  @JsonCreator
  public Task(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("incoming") Set<BaseElementId> incoming,
      @Nonnull @JsonProperty("outgoing") Set<BaseElementId> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
  }

  @JsonIgnore
  @Override
  public ProcessDefinition getAsSubProcessDefinition(ProcessDefinition parentProcessDefinition) {
    Map<BaseElementId, FlowElement> elements = new HashMap<>();
    elements.put(getId(), withoutLoopCharacteristics());
    BaseElementId sequenceFlowId = new BaseElementId(getId().getId() + "-to-end");
    BaseElementId endEventId = new BaseElementId(getId().getId() + "-end");
    elements.put(
        sequenceFlowId,
        new SequenceFlow(sequenceFlowId, getParentId(), getId(), endEventId, FlowCondition.NONE));
    elements.put(
        endEventId, new EndEvent(endEventId, getParentId(), Set.of(sequenceFlowId), Set.of()));

    // Wrap in Process element
    BaseElementId parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getProcessDefinitionId();
    Process process =
        new Process(
            parentProcessDefinitionId, parentProcessDefinitionId, new FlowElements(elements));

    Definitions definitions =
        new Definitions(
            parentProcessDefinition.getDefinitions().getProcessDefinitionId(),
            parentProcessDefinition.getDefinitions().getGeneration(),
            parentProcessDefinition.getDefinitions().getHash(),
            process);

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinition(definitions, version);
  }

  protected FlowElement withoutLoopCharacteristics() {
    return new Task(getId(), getParentId(), getIncoming(), getOutgoing(), LoopCharacteristics.NONE);
  }
}
