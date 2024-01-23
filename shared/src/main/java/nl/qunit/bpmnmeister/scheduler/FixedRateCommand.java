package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Getter
@Builder
public class FixedRateCommand implements ScheduleCommand {
  private final List<ProcessInstanceTrigger> triggers;
  private final String period;
  private final int repetitions;
  private final int repeatedCnt;
  private final String instantiation;

    @JsonCreator
  public FixedRateCommand(
          @JsonProperty("triggers") List<ProcessInstanceTrigger> triggers,
          @JsonProperty("period") String period,
          @JsonProperty("repetitions") int repetitions,
          @JsonProperty("repeatedCnt") int repeatedCnt,
          @JsonProperty("instantiation") String instantiation) {
      this.triggers = triggers;
      this.period = period;
      this.repetitions = repetitions;
      this.repeatedCnt = repeatedCnt;
      this.instantiation = instantiation;
  }

    @Override
    public FixedRateCommand evaluate(Instant now, Consumer<List<ProcessInstanceTrigger>> triggerConsumer) {
      Instant nextExecution = Instant.parse(instantiation).plus(Duration.parse(period));
      if (now.isAfter(nextExecution)) {
        // Time reached, return triggers
        triggerConsumer.accept(triggers);

        if (repeatedCnt < (repetitions - 1) || repetitions < 0) {
            // Return a new command with the next execution time
          return new FixedRateCommand(triggers, period, repetitions, repeatedCnt + 1, nextExecution.toString());
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
