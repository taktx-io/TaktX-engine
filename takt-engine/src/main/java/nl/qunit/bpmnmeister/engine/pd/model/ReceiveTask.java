package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ReceiveTaskInstance;

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
