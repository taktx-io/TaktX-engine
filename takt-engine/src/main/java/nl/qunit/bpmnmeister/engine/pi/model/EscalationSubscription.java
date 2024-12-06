package nl.qunit.bpmnmeister.engine.pi.model;

public record EscalationSubscription(String name, String code) {

  public boolean matchesEvent(EscalationEventSignal event) {
    return name.equals(event.getName()) && code.equals(event.getCode());
  }
}
