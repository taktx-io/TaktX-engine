package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public abstract class MessageEventDTO {

  private String messageName;

  protected MessageEventDTO(@Nonnull String messageName) {
    this.messageName = messageName;
  }

  @JsonIgnore
  public MessageEventKeyDTO toMessageEventKey() {
    return new MessageEventKeyDTO(messageName);
  }
}
