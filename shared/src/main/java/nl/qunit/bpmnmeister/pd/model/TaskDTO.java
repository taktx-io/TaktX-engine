package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class TaskDTO extends ActivityDTO {
  @JsonCreator
  public TaskDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristicsDTO loopCharacteristics,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
  }

  @JsonIgnore
  @Override
  public ProcessDefinitionDTO getAsSubProcessDefinition(
      ProcessDefinitionDTO parentProcessDefinition) {
    Map<String, FlowElementDTO> elements = new HashMap<>();
    elements.put(getId(), this);

    // Wrap in Process element
    String parentProcessDefinitionId =
        parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
    Process process =
        new Process(
            parentProcessDefinitionId, parentProcessDefinitionId, new FlowElementsDTO(elements));

    DefinitionsKey subDefinitionsKey =
        new DefinitionsKey(
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                + "/"
                + getId(),
            parentProcessDefinition.getDefinitions().getDefinitionsKey().getHash());
    DefinitionsDTO definitions =
        new DefinitionsDTO(
            subDefinitionsKey, process, parentProcessDefinition.getDefinitions().getMessages());

    Integer version = parentProcessDefinition.getVersion();
    return new ProcessDefinitionDTO(definitions, version, ProcessDefinitionStateEnum.ACTIVE);
  }

}
