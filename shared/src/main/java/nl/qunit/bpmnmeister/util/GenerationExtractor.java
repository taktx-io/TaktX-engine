package nl.qunit.bpmnmeister.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerationExtractor {
  private static final Pattern PATTERN = Pattern.compile("[gG]en(\\d+)");

  private GenerationExtractor() {
    // prevent instantiation
  }

  public static Optional<Integer> getGenerationFromString(String input) {
    Matcher matcher = PATTERN.matcher(input);
    if (matcher.find()) {
      return Optional.of(Integer.valueOf(matcher.group(1)));
    }
    return Optional.empty();
  }
}
