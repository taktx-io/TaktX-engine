package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class EventInstanceDTO extends FlowNodeInstanceDTO {
  protected EventInstanceDTO(UUID elementInstanceId, String elementId, int passedCnt) {
    super(elementInstanceId, elementId, passedCnt);
  }
}
