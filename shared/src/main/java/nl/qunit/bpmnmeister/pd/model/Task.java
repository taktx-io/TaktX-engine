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
    String sequenceFlowId = new String(getId() + "-to-end");
    elements.put(getId(), withoutLoopCharacteristics(Set.of(sequenceFlowId)));
    String endEventId = new String(getId() + "-end");
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

    Definitions definitions =
        new Definitions(
            parentProcessDefinition.getDefinitions().getDefinitionsKey(),
            process);

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  @Override
  public String getAsSubProcessStartElementId() {
    return getId();
  }

  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new Task(getId(), getId(), getIncoming(), outgoing, LoopCharacteristics.NONE);
  }
}
