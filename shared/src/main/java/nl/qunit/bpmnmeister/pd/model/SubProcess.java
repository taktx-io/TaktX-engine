package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;

@Getter
public class SubProcess extends Activity {

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
            new DefinitionsKey(parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                    + "-"
                    + getId(),
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash()),
            process);
    return new ProcessDefinition(definitions, version);
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SubProcess that = (SubProcess) o;
    return Objects.equals(elements, that.elements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), elements);
  }
}
