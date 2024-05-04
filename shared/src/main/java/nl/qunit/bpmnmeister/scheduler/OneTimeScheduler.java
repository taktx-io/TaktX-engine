package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class OneTimeScheduler implements MessageScheduler {
  private final List<SchedulableMessage<?>> messages;
  private final String when;

  @JsonCreator
  public OneTimeScheduler(
      @JsonProperty("messages") List<SchedulableMessage<?>> messages,
      @JsonProperty("when") String when) {
    this.messages = messages;
    this.when = when;
  }

  @Override
  public OneTimeScheduler evaluate(Instant now, Consumer<List<SchedulableMessage<?>>> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(messages);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }
}
