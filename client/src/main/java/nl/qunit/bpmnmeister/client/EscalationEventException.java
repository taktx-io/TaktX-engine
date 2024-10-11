package nl.qunit.bpmnmeister.client;

import lombok.Getter;

@Getter
public class EscalationEventException extends RuntimeException{

  private final String name;
  private final String code;

  public EscalationEventException(String name, String code, String message) {
    super(message);
    this.name = name;
    this.code = code;
  }
}
