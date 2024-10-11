package nl.qunit.bpmnmeister.client;

public class EscalationEvent extends RuntimeException {
  private final String code;

  public EscalationEvent(String code) {
    this.code = code;
  }
}
