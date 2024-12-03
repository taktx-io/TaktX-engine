package nl.qunit.bpmnmeister.client;

public class EscalationEventException extends RuntimeException {

  private final String name;
  private final String code;

  public EscalationEventException(String name, String code, String message) {
    super(message);
    this.name = name;
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }
}
