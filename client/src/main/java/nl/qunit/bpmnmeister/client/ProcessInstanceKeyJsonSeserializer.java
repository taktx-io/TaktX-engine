package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;

public class ProcessInstanceKeyJsonSeserializer extends JsonSerializer<ProcessInstanceKey> {

  public ProcessInstanceKeyJsonSeserializer() {
    super(ProcessInstanceKey.class);
  }
}
