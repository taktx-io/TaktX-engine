package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskState extends BpmnElementState {
  int cnt;

  public TaskState() {
  }

  public TaskState(StateEnum state, int cnt) {
    super(state);
    this.cnt = cnt;
  }

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(
        new TaskState(StateEnum.FINISHED, cnt + 1), bpmnElement.getOutputFlows(), Set.of());
  }
}
