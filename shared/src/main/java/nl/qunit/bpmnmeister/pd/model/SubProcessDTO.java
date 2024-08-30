package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SubProcessDTO extends ActivityDTO {

  private final FlowElementsDTO elements;

  @JsonCreator
  public SubProcessDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristicsDTO loopCharacteristics,
      @Nonnull @JsonProperty("elements") FlowElementsDTO elements,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {

    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.elements = elements;
  }

  @JsonIgnore
  @Override
  public ProcessDefinitionDTO getAsSubProcessDefinition(
      ProcessDefinitionDTO parentProcessDefinition) {
    Integer version = parentProcessDefinition.getVersion();
    String parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
    Process process = new Process(parentProcessDefinitionId, parentProcessDefinitionId, elements);
    DefinitionsDTO definitions =
        new DefinitionsDTO(
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
    return new ProcessDefinitionDTO(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

}
