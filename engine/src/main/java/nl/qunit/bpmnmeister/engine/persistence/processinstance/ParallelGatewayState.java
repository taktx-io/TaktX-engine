package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ParallelGateway;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode(callSuper = true)
public class ParallelGatewayState extends BpmnElementState {
  Set<String> triggeredFlows;

  public ParallelGatewayState() {
    this.triggeredFlows = new HashSet<>();
  }

  public ParallelGatewayState(StateEnum state, Set<String> triggeredFlows) {
    super(state);
    this.triggeredFlows = triggeredFlows;
  }

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    Set<String> newTriggeredFlows = new HashSet<>(triggeredFlows);
    newTriggeredFlows.add(trigger.inputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    if (bpmnElement instanceof ParallelGateway parallelGateway
        && (parallelGateway.getInputFlows().equals(newTriggeredFlows))) {
      newTriggeredFlows.clear();
      outputFlows.addAll(parallelGateway.getOutputFlows());
    }
    return new TriggerResult(new ParallelGatewayState(state, newTriggeredFlows), outputFlows, Set.of());
  }
}
