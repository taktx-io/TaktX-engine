package nl.qunit.bpmnmeister.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

public class Util {

  private Util() {}

  public static Duration calculateInitialDelay(
      LocalDateTime now, LocalDateTime when, Duration period) {
    var internalWhen = when;
    if (internalWhen.isBefore(now)) {
      while (internalWhen.isBefore(now)) {
        internalWhen = internalWhen.plus(period);
      }
    }
    return Duration.between(now, internalWhen);
  }

  public static Headers headersWithout(Headers headers, Iterable<String> without) {
    var headersWithout = new RecordHeaders(headers);
    without.forEach(headersWithout::remove);
    return headersWithout;
  }
}
