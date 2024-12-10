package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class TerminateEventDefinitionDTO extends EventDefinitionDTO {

  @JsonCreator
  public TerminateEventDefinitionDTO(@Nonnull @JsonProperty("id") String id) {
    super(id, Constants.NONE);
  }
}
