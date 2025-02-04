package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.ReceiveTaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ReceiveTask extends Activity implements WithMessageReference {
  private String messageRef;

  @Setter private Message referencedMessage;

  @Override
  public ActivityInstance<?> newActivityInstance(FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new ReceiveTaskInstance(parentInstance, this, elementInstanceId);
  }
}
