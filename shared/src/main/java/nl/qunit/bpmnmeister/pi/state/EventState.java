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
public abstract class EventState extends FlowNodeStateDTO {
  protected EventState(
      UUID elementInstanceId, String elementId, int passedCnt, String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, inputFlowId);
  }
}
