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
import nl.qunit.bpmnmeister.pi.state.CallActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CallActivity extends Activity<CallActivityState> {

  @Nonnull private final String calledElement;

  @JsonCreator
  public CallActivity(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("calledElement") String calledElement) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.calledElement = calledElement;
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
    DefinitionsKey existingDefinitionsKey =
        parentProcessDefinition.getDefinitions().getDefinitionsKey();
    String parentProcessDefinitionId = existingDefinitionsKey.getProcessDefinitionId();
    Process process =
        new Process(
            parentProcessDefinitionId, parentProcessDefinitionId, new FlowElements(elements));

    DefinitionsKey subDefinitionsKey =
        new DefinitionsKey(
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                + "-"
                + getId(),
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash());
    Definitions definitions = new Definitions(subDefinitionsKey, process, Map.of());

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  @Override
  public String getAsSubProcessStartElementId() {
    return getId();
  }

  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new CallActivity(
        getId(), getId(), getIncoming(), outgoing, LoopCharacteristics.NONE, calledElement);
  }

  @Override
  public CallActivityState getInitialState(String inputFlowId, int passedCnt) {
    return new CallActivityState(FlowNodeStateEnum.READY, UUID.randomUUID(), passedCnt, 0, inputFlowId);
  }
}
