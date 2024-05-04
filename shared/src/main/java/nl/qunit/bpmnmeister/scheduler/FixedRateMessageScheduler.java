package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class FixedRateMessageScheduler implements MessageScheduler {
  private final List<SchedulableMessage<?>> messages;
  private final String period;
  private final int repetitions;
  private final int repeatedCnt;
  private final String instantiation;

  @JsonCreator
  public FixedRateMessageScheduler(
      @JsonProperty("messages") List<SchedulableMessage<?>> messages,
      @JsonProperty("period") String period,
      @JsonProperty("repetitions") int repetitions,
      @JsonProperty("repeatedCnt") int repeatedCnt,
      @JsonProperty("instantiation") String instantiation) {
    this.messages = messages;
    this.period = period;
    this.repetitions = repetitions;
    this.repeatedCnt = repeatedCnt;
    this.instantiation = instantiation;
  }

  @Override
  public FixedRateMessageScheduler evaluate(Instant now, Consumer<List<SchedulableMessage<?>>> triggerConsumer) {
    Instant instant = Instant.parse(this.instantiation);
    if (now.isAfter(instant)) {
      // Time reached, return triggers
      triggerConsumer.accept(messages);

      if (repeatedCnt < (repetitions - 1) || repetitions < 0) {
        // Return a new command with the next execution time
        Instant nextExecution = instant.plus(Duration.parse(period));
        if (now.isAfter(nextExecution)) {
          // If the next execution time is already in the past, skip to the next one
          nextExecution = now.plus(Duration.parse(period));
        }
        return new FixedRateMessageScheduler(
            messages, period, repetitions, repeatedCnt + 1, nextExecution.toString());
      } else {
        // Return null to indicate that this command is done
        return null;
      }
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }
}
