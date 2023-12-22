package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceTaskState extends BpmnElementState {
  int cnt;

  public ServiceTaskState() {
    this.cnt = 0;
  }

  public ServiceTaskState(StateEnum newState, int cnt) {
    super(newState);
    this.cnt = cnt;
  }


  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    StateEnum newState = state;
    Set<String> newActiveFlows = new HashSet<>();
    Set<String> externalTasks = new HashSet<>();

    if (state == StateEnum.INIT) {
      newState = StateEnum.WAITING;
      externalTasks.add(bpmnElement.getId());
    } else if (state == StateEnum.WAITING) {
      newActiveFlows.addAll(bpmnElement.getOutputFlows());
      newState = StateEnum.FINISHED;
    }

    return new TriggerResult(
        new ServiceTaskState(newState, cnt + 1), newActiveFlows, externalTasks);
  }
}
