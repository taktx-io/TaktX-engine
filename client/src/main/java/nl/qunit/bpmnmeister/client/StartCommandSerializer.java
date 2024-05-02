package nl.qunit.bpmnmeister.client;

import nl.qunit.bpmnmeister.pi.StartCommand;

public class StartCommandSerializer extends JsonSerializer<StartCommand> {

  public StartCommandSerializer() {
    super(StartCommand.class);
  }
}
