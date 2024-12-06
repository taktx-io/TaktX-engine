package nl.qunit.bpmnmeister.engine.pi.model;

public record ErrorSubscription(String name, String code) {

  public boolean matchesEvent(ErrorEventSignal event) {
    return name.equals(event.getName()) && code.equals(event.getCode());
  }
}
