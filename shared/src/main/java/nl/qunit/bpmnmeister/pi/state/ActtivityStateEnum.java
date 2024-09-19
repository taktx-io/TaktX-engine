package nl.qunit.bpmnmeister.pi.state;

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
