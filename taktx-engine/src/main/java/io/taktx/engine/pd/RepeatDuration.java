/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import lombok.Getter;

@Getter
public class RepeatDuration {
  private final int repetitions;
  private final Duration duration;

  private RepeatDuration(int repetitions, Duration duration) {
    this.repetitions = repetitions;
    this.duration = duration;
  }

  public static RepeatDuration parse(String text) {
    String[] parts = text.split("/");
    try {
      if (parts.length == 1) {
        String part = parts[0].trim();
        return new RepeatDuration(-1, Duration.parse(part));
      } else if (parts.length == 2) {
        String repetitionString = parts[0].trim(); // remove 'R'
        if (repetitionString.isBlank()) {
          String part = parts[1].trim();

          return new RepeatDuration(-1, Duration.parse(part));
        } else {
          if (repetitionString.charAt(0) != 'R') {
            throw new DateTimeParseException(
                "Repeat duration expression should start with 'R'" + repetitionString,
                repetitionString,
                0);
          } else {
            String part = parts[1].trim();

            if (repetitionString.length() == 1) {
              return new RepeatDuration(-1, Duration.parse(part));
            } else {
              int repetitions = Integer.parseInt(repetitionString.substring(1).trim());
              return new RepeatDuration(repetitions, Duration.parse(part));
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
