package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class ProcessDefinition {
  public static final ProcessDefinition NULL = new ProcessDefinition(Definitions.NULL, -1);
  private final Definitions definitions;
  private final Integer version;

  @JsonCreator
  public ProcessDefinition(
      @JsonProperty("definitions") @Nonnull Definitions definitions,
      @JsonProperty("id") @Nonnull Integer version) {
    this.definitions = definitions;
    this.version = version;
  }

  @Override
  public String toString() {
    return "ProcessDefinition{" + "definitions=" + definitions + ", version=" + version + '}';
  }
}
