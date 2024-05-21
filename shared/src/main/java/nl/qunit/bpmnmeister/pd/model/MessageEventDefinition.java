package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class MessageEventDefinition extends EventDefinition {

  private final String messageRef;

  @JsonCreator
  public MessageEventDefinition(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("messageRef") String messageRef) {
    super(id, Constants.NONE);
    this.messageRef = messageRef;
  }

}
