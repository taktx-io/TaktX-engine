package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ProcessDefinition {

  public static final ProcessDefinition NONE = new ProcessDefinition(Definitions.NONE, -1,
      ProcessDefinitionStateEnum.INACTIVE);

  private final Definitions definitions;
  private final Integer version;
  private final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinition(
      @Nonnull @JsonProperty("definitions") Definitions definitions,
      @Nonnull @JsonProperty("id") Integer version,
      @Nonnull @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessDefinition)) {
      return false;
    }
    final ProcessDefinition other = (ProcessDefinition) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$definitions = this.getDefinitions();
    final Object other$definitions = other.getDefinitions();
    if (this$definitions == null ? other$definitions != null
        : !this$definitions.equals(other$definitions)) {
      return false;
    }
    final Object this$version = this.getVersion();
    final Object other$version = other.getVersion();
    if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDefinition;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $definitions = this.getDefinitions();
    result = result * PRIME + ($definitions == null ? 43 : $definitions.hashCode());
    final Object $version = this.getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    return result;
  }
}
