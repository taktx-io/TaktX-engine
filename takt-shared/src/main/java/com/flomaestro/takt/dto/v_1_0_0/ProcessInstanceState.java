package com.flomaestro.takt.dto.v_1_0_0;

public enum ProcessInstanceState {
  START,
  ACTIVE,
  COMPLETED,
  TERMINATED,
  FAILED;

  public boolean isFinished() {
    return this == COMPLETED || this == TERMINATED || this == FAILED;
  }
}
