package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class EscalationEventDefinitionDTO extends EventDefinitionDTO {

  private final String escalationRef;

  @JsonCreator
  public EscalationEventDefinitionDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("messageRef") String escalationRef) {
    super(id, Constants.NONE);
    this.escalationRef = escalationRef;
  }
}
