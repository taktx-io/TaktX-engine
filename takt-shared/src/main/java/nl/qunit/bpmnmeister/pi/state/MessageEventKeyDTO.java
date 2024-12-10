package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class MessageEventKeyDTO {

  private String messageName;

  @JsonCreator
  public MessageEventKeyDTO(@JsonProperty("messageName") String messageName) {
    this.messageName = messageName;
  }

  @Override
  public String toString() {
    return super.toString();
  }
}
