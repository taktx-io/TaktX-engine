package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DefinitionMessageEventTrigger.class),
  @JsonSubTypes.Type(value = CorrelationMessageEventTrigger.class),
  @JsonSubTypes.Type(value = DefinitionMessageSubscription.class),
  @JsonSubTypes.Type(value = CorrelationMessageSubscription.class)
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
