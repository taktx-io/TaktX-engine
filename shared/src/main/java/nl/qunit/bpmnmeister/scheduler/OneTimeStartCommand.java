package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class OneTimeStartCommand implements ScheduleCommand {
  private final List<SchedulableMessage> schedulableMessages;
  private final String when;

  @JsonCreator
  public OneTimeStartCommand(
      @JsonProperty("startCommands") List<SchedulableMessage> messages,
      @JsonProperty("when") String when) {
    this.schedulableMessages = messages;
    this.when = when;
  }

  @Override
  public OneTimeStartCommand evaluate(Instant now, Consumer<List<SchedulableMessage>> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(schedulableMessages);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }
}
