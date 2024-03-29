package nl.qunit.bpmnmeister.pi;

public enum ProcessInstanceState {
  START,
  ACTIVE,
  SUSPENDED,
  COMPLETED,
  TERMINATED,
  FAILED;

  public boolean isFinished() {
    return this == COMPLETED || this == TERMINATED || this == FAILED;
  }
}
