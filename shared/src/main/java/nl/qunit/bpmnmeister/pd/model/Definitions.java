package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class Definitions {
  public static final Definitions NONE =
      new Definitions(BaseElementId.NONE, 0, "", FlowElements.EMPTY);

  private final BaseElementId processDefinitionId;
  private final Integer generation;
  private final String hash;
  private final FlowElements elements;

  public Definitions(
      @JsonProperty("processDefinitionId") @Nonnull BaseElementId processDefinitionId,
      @JsonProperty("generation") @Nonnull Integer generation,
      @JsonProperty("hash") @Nonnull String hash,
      @JsonProperty("elements") @Nonnull FlowElements elements) {
    this.processDefinitionId = processDefinitionId;
    this.generation = generation;
    this.hash = hash;
    this.elements = elements;
  }

  @Override
  public String toString() {
    return "Definitions{"
        + "processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", generation='"
        + generation
        + '\''
        + ", hash='"
        + hash
        + '\''
        + ", elements="
        + elements
        + '}';
  }
}
