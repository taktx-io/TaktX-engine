package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ExternalTask;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance<N extends ExternalTask> extends ActivityInstance<N>
    implements FlowNodeInstanceWithScheduleKeys {
  private int attempt;
  private List<ScheduleKeyDTO> scheduledKeys = new ArrayList<>();

  public ExternalTaskInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public int increaseAttempt() {
    setDirty();
    return ++attempt;
  }

  @Override
  public void addScheduledKey(ScheduleKeyDTO scheduledKey) {
    this.scheduledKeys.add(scheduledKey);
  }

  public void clearScheduledKeys() {
    this.scheduledKeys.clear();
  }
}
