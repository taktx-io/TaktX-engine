package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
public class ProcessDefinition {
  public static final ProcessDefinition NONE = new ProcessDefinition(Definitions.NONE, -1);

  private final Definitions definitions;
  private final Integer version;

  @JsonCreator
  public ProcessDefinition(
      @Nonnull @JsonProperty("definitions") Definitions definitions,
      @Nonnull @JsonProperty("id") Integer version) {
    this.definitions = definitions;
    this.version = version;
  }

  @Override
  public String toString() {
    return "ProcessDefinition{" + "definitions=" + definitions + ", version=" + version + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProcessDefinition that = (ProcessDefinition) o;
    return Objects.equals(definitions, that.definitions) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(definitions, version);
  }
}
