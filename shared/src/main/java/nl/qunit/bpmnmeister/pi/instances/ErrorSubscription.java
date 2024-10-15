package nl.qunit.bpmnmeister.pi.instances;

public record ErrorSubscription(String name, String code) {

  public boolean matchesEvent(ErrorEventSignal event) {
    return name.equals(event.getName()) && code.equals(event.getCode());
  }
}
