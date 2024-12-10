package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventDTO;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DefinitionMessageSubscriptionDTO extends MessageEventDTO {
  private ProcessDefinitionKey processDefinitionKey;
  private String elementId;

  @JsonCreator
  public DefinitionMessageSubscriptionDTO(
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("messageName") String messageName) {
    super(messageName);
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }
}
