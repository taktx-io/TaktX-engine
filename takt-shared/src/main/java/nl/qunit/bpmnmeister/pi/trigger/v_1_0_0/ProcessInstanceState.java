package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

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
