package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventDTO;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CancelDefinitionMessageSubscriptionDTO extends MessageEventDTO {
  @JsonCreator
  public CancelDefinitionMessageSubscriptionDTO(
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
  }
}
