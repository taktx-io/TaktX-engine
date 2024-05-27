package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.CancelCorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DefinitionMessageEventTrigger.class),
  @JsonSubTypes.Type(value = CorrelationMessageEventTrigger.class),
  @JsonSubTypes.Type(value = DefinitionMessageSubscription.class),
  @JsonSubTypes.Type(value = CancelDefinitionMessageSubscription.class),
  @JsonSubTypes.Type(value = CorrelationMessageSubscription.class),
  @JsonSubTypes.Type(value = CancelCorrelationMessageSubscription.class)
})
@Getter
@EqualsAndHashCode
public abstract class MessageEvent {

  private final String messageName;

  protected MessageEvent(@Nonnull String messageName) {
    this.messageName = messageName;
  }

  public MessageEventKey getKey() {
    return new MessageEventKey(messageName);
  }
}
