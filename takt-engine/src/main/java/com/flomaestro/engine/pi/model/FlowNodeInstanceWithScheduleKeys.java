package com.flomaestro.engine.pi.model;

import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;

public interface FlowNodeInstanceWithScheduleKeys extends IFlowNodeInstance {
  void addScheduledKey(ScheduleKeyDTO scheduledKey);
}
