package io.taktx.dto.v_1_0_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class FixedRateMessageScheduleDTOTest {

  public static final String START_TIME = "2021-01-01T00:00:00Z";

  @ParameterizedTest
  @MethodSource("testNextExecutionTimeNoRepetitions")
  void testNextExecutionTimeRepetitions(
      int repetitions, Long from, Long expectedNextExecutionTime) {
    SchedulableMessageDTO message = Mockito.mock(SchedulableMessageDTO.class);

    Instant start = Instant.parse(START_TIME);

    FixedRateMessageScheduleDTO schedule =
        new FixedRateMessageScheduleDTO(message, 1000, repetitions, start.toEpochMilli());
    assertThat(schedule.getNextExecutionTime(from)).isEqualTo(expectedNextExecutionTime);
  }

  // Source for the parameterized test
  static Object[][] testNextExecutionTimeNoRepetitions() {
    return new Object[][] {
      {
        9,
        Instant.parse("2021-01-01T00:00:00Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:01Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:00.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:01Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:01Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:02Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:01.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:02Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:02Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:03Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:02.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:03Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:03Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:04Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:03.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:04Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:04Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:05Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:04.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:05Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:05Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:06Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:05.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:06Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:06Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:07Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:06.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:07Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:07Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:08Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:07.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:08Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:08Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:09Z").toEpochMilli()
      },
      {
        9,
        Instant.parse("2021-01-01T00:00:08.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:09Z").toEpochMilli()
      },
      {9, Instant.parse("2021-01-01T00:00:09Z").toEpochMilli(), null},
      {9, Instant.parse("2021-01-01T00:00:09.999Z").toEpochMilli(), null},

      // No repetitions continues indefinately
      {
        0,
        Instant.parse("2021-01-01T00:00:00Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:01Z").toEpochMilli()
      },
      {
        0,
        Instant.parse("2021-01-01T00:00:00.999Z").toEpochMilli(),
        Instant.parse("2021-01-01T00:00:01Z").toEpochMilli()
      },
      {
        0,
        Instant.parse("2028-01-01T00:00:00Z").toEpochMilli(),
        Instant.parse("2028-01-01T00:00:01Z").toEpochMilli()
      },
      {
        0,
        Instant.parse("2028-01-01T00:00:00.999Z").toEpochMilli(),
        Instant.parse("2028-01-01T00:00:01Z").toEpochMilli()
      },
    };
  }
}
