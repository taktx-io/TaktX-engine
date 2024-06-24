package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class TerminateEventDefinition extends EventDefinition {

  @JsonCreator
  public TerminateEventDefinition(
      @Nonnull @JsonProperty("id") String id) {
    super(id, Constants.NONE);
  }
}
