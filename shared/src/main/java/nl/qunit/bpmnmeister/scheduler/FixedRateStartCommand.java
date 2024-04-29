package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.StartCommand;

@Getter
public class FixedRateStartCommand implements ScheduleStartCommand {
  private final List<StartCommand> startCommands;
  private final String period;
  private final int repetitions;
  private final int repeatedCnt;
  private final String instantiation;

  @JsonCreator
  public FixedRateStartCommand(
      @JsonProperty("triggers") List<StartCommand> startCommands,
      @JsonProperty("period") String period,
      @JsonProperty("repetitions") int repetitions,
      @JsonProperty("repeatedCnt") int repeatedCnt,
      @JsonProperty("instantiation") String instantiation) {
    this.startCommands = startCommands;
    this.period = period;
    this.repetitions = repetitions;
    this.repeatedCnt = repeatedCnt;
    this.instantiation = instantiation;
  }

  @Override
  public FixedRateStartCommand evaluate(Instant now, Consumer<List<StartCommand>> triggerConsumer) {
    Instant nextExecution = Instant.parse(instantiation).plus(Duration.parse(period));
    if (now.isAfter(nextExecution)) {
      // Time reached, return triggers
      triggerConsumer.accept(startCommands);

      if (repeatedCnt < (repetitions - 1) || repetitions < 0) {
        // Return a new command with the next execution time
        return new FixedRateStartCommand(
            startCommands, period, repetitions, repeatedCnt + 1, nextExecution.toString());
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
