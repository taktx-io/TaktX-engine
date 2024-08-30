package nl.qunit.bpmnmeister.pi.state;

public enum FlowNodeStateEnum {
  READY,
  WAITING,
  TERMINATED,
  FINISHED;

  public boolean isFinished() {
    return this == FINISHED || this == TERMINATED;
  }
}
