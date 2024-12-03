package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ErrorEventDefinitionDTO extends EventDefinitionDTO {

  private final String errorRef;

  @JsonCreator
  public ErrorEventDefinitionDTO(
      @Nonnull @JsonProperty("id") String id, @Nonnull @JsonProperty("errorRef") String errorRef) {
    super(id, Constants.NONE);
    this.errorRef = errorRef;
  }
}
