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
public class ExclusiveGatewayState extends BpmnElementState {
  public ExclusiveGatewayState() {
  }

  public ExclusiveGatewayState(StateEnum state) {
    super(state);
  }


  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(new ExclusiveGatewayState(StateEnum.FINISHED), bpmnElement.getOutputFlows(), Set.of());
  }
}
