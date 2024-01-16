package nl.qunit.bpmnmeister.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class GenerationExtractor {
  private static final Pattern PATTERN = Pattern.compile("Gen(\\d+)");

  public Optional<String> getGenerationFromString(String input) {
    Matcher matcher = PATTERN.matcher(input);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }
}
