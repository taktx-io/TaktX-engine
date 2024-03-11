package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import lombok.Getter;

@Getter
public class Definitions implements WithElements {
  public static final Definitions NULL = new Definitions(BaseElementId.NULL, 0, "", Map.of());

  private final BaseElementId processDefinitionId;
  private final Integer generation;
  private final String hash;
  private final Map<BaseElementId, BaseElement> elements;

  public Definitions(
      @JsonProperty("processDefinitionId") @Nonnull BaseElementId processDefinitionId,
      @JsonProperty("generation") @Nonnull Integer generation,
      @JsonProperty("hash") @Nonnull String hash,
      @JsonProperty("elements") @Nonnull Map<BaseElementId, BaseElement> elements) {
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
