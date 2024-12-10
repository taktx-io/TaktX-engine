package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventDTO;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageSubscriptionDTO extends MessageEventDTO {

  private UUID processInstanceKey;
  private String correlationKey;
  private List<String> elementIdPath;
  private List<UUID> elementInstanceIdPath;

  @JsonCreator
  public CorrelationMessageSubscriptionDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("correlationKey") String correlationKey,
      @Nonnull @JsonProperty("elementIdPath") List<String> elementIdPath,
      @Nonnull @JsonProperty("elementInstanceId") List<UUID> elementInstanceIdPath,
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementIdPath = elementIdPath;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
