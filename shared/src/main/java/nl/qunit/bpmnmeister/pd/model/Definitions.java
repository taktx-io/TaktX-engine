package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class Definitions {
  public static final Definitions NONE =
      new Definitions(BaseElementId.NONE, 0, "", Process.NONE);

  private final BaseElementId processDefinitionId;
  private final Integer generation;
  private final String hash;
  private final Process rootProcess;

  @JsonCreator
  public Definitions(
      @JsonProperty("processDefinitionId") @Nonnull BaseElementId processDefinitionId,
      @JsonProperty("generation") @Nonnull Integer generation,
      @JsonProperty("hash") @Nonnull String hash,
      @JsonProperty("elements") @Nonnull Process rootProcess) {
    this.processDefinitionId = processDefinitionId;
    this.generation = generation;
    this.hash = hash;
    this.rootProcess = rootProcess;
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
        + ", rootProcess="
        + rootProcess
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Definitions that = (Definitions) o;
    return Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(generation, that.generation) && Objects.equals(hash,
        that.hash) && Objects.equals(rootProcess, that.rootProcess);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionId, generation, hash, rootProcess);
  }
}
