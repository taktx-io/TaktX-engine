package nl.qunit.bpmnmeister.pi.state;

public enum FlowNodeStateEnum {
  READY,
  ACTIVE,
  TERMINATED,
  FINISHED;

  public boolean isFinished() {
    return this == FINISHED || this == TERMINATED;
  }
}
