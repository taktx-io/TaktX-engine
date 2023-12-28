package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.*;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Getter
@BsonDiscriminator
@SuperBuilder
public class ServiceTask extends Task {

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElementState bpmnElementState) {
    Set<String> newActiveFlows = new HashSet<>();
    Set<String> externalTasks = new HashSet<>();
    ServiceTaskState serviceTaskState = (ServiceTaskState) bpmnElementState;
    if (serviceTaskState.getState() == StateEnum.INIT) {
      externalTasks.add(getId());
    } else if (serviceTaskState.getState() == StateEnum.WAITING) {
      newActiveFlows.addAll(getOutgoing());
    }

    return new TriggerResult(
        ServiceTaskState.builder().cnt(serviceTaskState.getCnt() + 1).build(),
        newActiveFlows,
        externalTasks);
  }
}
