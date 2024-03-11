package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public class SubProcess extends Activity implements WithElements {

  private final Map<BaseElementId, BaseElement> elements;

  @JsonCreator
  public SubProcess(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("incoming") Set<BaseElementId> incoming,
      @Nonnull @JsonProperty("outgoing") Set<BaseElementId> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("elements") Map<BaseElementId, BaseElement> elements) {

    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.elements = elements;
  }

  @JsonIgnore
  @Override
  public ProcessDefinition getAsSubProcessDefinition(ProcessDefinition parentProcessDefinition) {
    Integer version = parentProcessDefinition.getVersion();
    Definitions definitions =
        new Definitions(
            new BaseElementId(
                parentProcessDefinition.getDefinitions().getProcessDefinitionId().getId()
                    + "-"
                    + getId().getId()),
            parentProcessDefinition.getDefinitions().getGeneration(),
            parentProcessDefinition.getDefinitions().getHash(),
            elements);
    return new ProcessDefinition(definitions, version);
  }
}
