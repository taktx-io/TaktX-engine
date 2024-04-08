package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Definitions {
  public static final Definitions NONE = new Definitions(DefinitionsKey.NONE, Process.NONE);
  private final DefinitionsKey definitionsKey;
  private final Process rootProcess;

  @JsonCreator
  public Definitions(
      @JsonProperty("definitionsKey") @Nonnull DefinitionsKey definitionsKey,
      @JsonProperty("elements") @Nonnull Process rootProcess) {
    this.definitionsKey = definitionsKey;
    this.rootProcess = rootProcess;
  }

}
