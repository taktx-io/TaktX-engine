package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.StartCommand;

@Getter
public class OneTimeStartCommand implements ScheduleStartCommand {
  private final List<StartCommand> startCommands;
  private final String when;

  @JsonCreator
  public OneTimeStartCommand(
      @JsonProperty("startCommands") List<StartCommand> startCommands,
      @JsonProperty("when") String when) {
    this.startCommands = startCommands;
    this.when = when;
  }

  @Override
  public OneTimeStartCommand evaluate(
      Instant now, Consumer<List<StartCommand>> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(startCommands);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }
}
