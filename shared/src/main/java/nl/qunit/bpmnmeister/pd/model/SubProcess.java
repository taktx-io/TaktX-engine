package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
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
      @Nonnull @JsonProperty("elements") FlowElements elements,
      @Nonnull @JsonProperty("ioMapping") InputOutputMapping ioMapping) {

    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
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
                    + "/"
                    + getId(),
                parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash()),
            process,
            parentProcessDefinition.getDefinitions().getMessages());
    return new ProcessDefinition(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

  @Override
  public SubProcessState getInitialState(UUID parentElementInstanceId, String elementId,
      String inputFlowId, int passedCnt) {
    return new SubProcessState(
        FlowNodeStateEnum.READY, Constants.NONE_UUID, parentElementInstanceId, UUID.randomUUID(), elementId, passedCnt, 0, inputFlowId);
  }
}
