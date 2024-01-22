package nl.qunit.bpmnmeister.scheduler;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import lombok.Getter;

@Getter
public class RepeatDuration {
  private final int repetitions;
  private final String duration;

  public RepeatDuration(int repetitions, String duration) {
    this.repetitions = repetitions;
    this.duration = duration;
  }

  public static RepeatDuration parse(String text) {
    String[] parts = text.split("/");
    try {
      if (parts.length == 1) {
        String part = parts[0].trim();
        Duration.parse(part);
        return new RepeatDuration(-1, part);
      } else if (parts.length == 2) {
        String repetitionString = parts[0].trim(); // remove 'R'
        if (repetitionString.isBlank()) {
          String part = parts[1].trim();
          Duration.parse(part);
          return new RepeatDuration(-1, part);
        } else {
          if (repetitionString.charAt(0) != 'R') {
            throw new DateTimeParseException(
                "Repeat duration expression should start with 'R'" + repetitionString,
                repetitionString,
                0);
          } else {
            String part = parts[1].trim();
            Duration.parse(part);
            if (repetitionString.length() == 1) {
              return new RepeatDuration(-1, part);
            } else {
              int repetitions = Integer.parseInt(repetitionString.substring(1).trim());
              return new RepeatDuration(repetitions, part);
            }
          }
        }
      } else {
        throw new DateTimeParseException("Invalid repeat duration expression" + text, text, 0);
      }
    } catch (DateTimeParseException e) {
      throw new DateTimeParseException("Invalid repeat duration expression" + text, text, 0);
    }
  }
}
