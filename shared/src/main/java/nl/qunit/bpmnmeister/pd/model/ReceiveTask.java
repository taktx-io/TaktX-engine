package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.ReceiveTaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ReceiveTask extends Activity implements WithMessageReference {
  private String messageRef;

  @Setter private Message referencedMessage;

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new ReceiveTaskInstance(parentInstance, this);
  }
}
