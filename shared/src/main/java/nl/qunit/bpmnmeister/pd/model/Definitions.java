package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder
public class Definitions extends DefinitionsTrigger {
  public static final Definitions NONE = new Definitions(DefinitionsKey.NONE, Process.NONE, Map.of());
  private final DefinitionsKey definitionsKey;
  private final Process rootProcess;
  private final Map<String, Message> messages;

  @JsonCreator
  public Definitions(
      @JsonProperty("definitionsKey") @Nonnull DefinitionsKey definitionsKey,
      @JsonProperty("elements") @Nonnull Process rootProcess,
      @JsonProperty("messages") @Nonnull Map<String, Message> messages
      ) {
    this.definitionsKey = definitionsKey;
    this.rootProcess = rootProcess;
    this.messages = messages;
  }
}
