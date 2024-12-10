package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

public enum ActtivityStateEnum {
  READY,
  WAITING,
  TERMINATED,
  FAILED,
  FINISHED;

  public boolean isFinished() {
    return this == FAILED || this == FINISHED || this == TERMINATED;
  }
}
