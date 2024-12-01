package nl.qunit.bpmnmeister.pi;

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
