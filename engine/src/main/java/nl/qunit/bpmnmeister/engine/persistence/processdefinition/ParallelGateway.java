package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.*;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Getter
@SuperBuilder
public class ParallelGateway extends Gateway {

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElementState oldState) {
    ParallelGatewayState parallelGatewayState = (ParallelGatewayState) oldState;
    Set<String> newTriggeredFlows = new HashSet<>(parallelGatewayState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.inputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    StateEnum newState = StateEnum.ACTIVE;
    if (getOutgoing().equals(newTriggeredFlows)) {
      newState = StateEnum.FINISHED;
      newTriggeredFlows.clear();
      outputFlows.addAll(getOutgoing());
    }
    return new TriggerResult(
        ParallelGatewayState.builder().triggeredFlows(newTriggeredFlows).state(newState).build(),
        outputFlows,
        Set.of());
  }
}
