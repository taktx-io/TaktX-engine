/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

// java
// Add to `build.gradle`:
// implementation 'io.github.classgraph:classgraph:4.8.154'

package io.taktx.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ResourceScanner {

  public static List<Resource> getResources(String pattern) {
    String p = stripClasspathPrefix(pattern);
    String regex = antToRegex(p);
    Pattern compiled = Pattern.compile(regex);

    List<Resource> results = new ArrayList<>();
    try (ScanResult scanResult = new ClassGraph().scan()) {
      results.addAll(scanResult.getResourcesMatchingPattern(compiled));
    }
    return results;
  }

  public static List<Path> getFileSystemResources(String pattern) throws IOException {
    String p = stripFilePrefix(pattern);
    String normalizedPattern = p.replace(File.separatorChar, '/');
    // If it's a plain path with no wildcards, just return the file if it exists
    if (!containsWildcard(normalizedPattern)) {
      Path single = Paths.get(normalizedPattern);
      List<Path> singleResult = new ArrayList<>();
      if (Files.exists(single) && Files.isRegularFile(single)) {
        singleResult.add(single);
      }
      return singleResult;
    }

    String regex = antToRegex(normalizedPattern);
    Pattern compiled = Pattern.compile(regex);
    boolean patternIsAbsolute = normalizedPattern.startsWith("/");

    // determine base directory to start walking from (up to the last slash before the first
    // wildcard)
    int firstWildcard = firstWildcardIndex(normalizedPattern);
    int slashBefore = normalizedPattern.lastIndexOf('/', firstWildcard >= 0 ? firstWildcard : 0);
    String baseDirStr = slashBefore >= 0 ? normalizedPattern.substring(0, slashBefore) : ".";
    Path start = Paths.get(baseDirStr);

    List<Path> results = new ArrayList<>();
    if (!Files.exists(start)) {
      return results;
    }

    try (Stream<Path> stream = Files.walk(start)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                String candidate;
                if (patternIsAbsolute) {
                  candidate =
                      path.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/');
                } else {
                  try {
                    candidate =
                        start
                            .toAbsolutePath()
                            .normalize()
                            .relativize(path.toAbsolutePath().normalize())
                            .toString()
                            .replace(File.separatorChar, '/');
                  } catch (IllegalArgumentException e) {
                    // fallback to full absolute path if relativize fails
                    candidate =
                        path.toAbsolutePath()
                            .normalize()
                            .toString()
                            .replace(File.separatorChar, '/');
                  }
                }
                if (compiled.matcher(candidate).matches()) {
                  results.add(path);
                }
              });
    }

    return results;
  }

  private static String stripClasspathPrefix(String pattern) {
    String trimmed = pattern.trim();
    if (trimmed.startsWith("classpath*:")) {
      return trimmed.substring("classpath*:".length());
    } else if (trimmed.startsWith("classpath:")) {
      return trimmed.substring("classpath:".length());
    }
    return trimmed;
  }

  private static String stripFilePrefix(String pattern) {
    String trimmed = pattern.trim();
    if (trimmed.startsWith("file:")) {
      return trimmed.substring("file:".length());
    }
    return trimmed;
  }

  private static boolean containsWildcard(String s) {
    return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }

  private static int firstWildcardIndex(String s) {
    int a = s.indexOf('*');
    int b = s.indexOf('?');
    if (a == -1) return b;
    if (b == -1) return a;
    return Math.min(a, b);
  }

  /**
   * Convert a simple Ant-style pattern to a regex suitable for ClassGraph resource matching.
   * Supports "*" (matches within a path segment), "**" (matches across segments) and "?".
   */
  private static String antToRegex(String ant) {
    String s = ant;
    // escape regex metacharacters except our wildcards
    s =
        s.replace("\\", "\\\\")
            .replace(".", "\\.")
            .replace("$", "\\$")
            .replace("^", "\\^")
            .replace("+", "\\+")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("|", "\\|")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("/", "/"); // keep slash

    // Handle special wildcard sequences
    // Replace "**/" with "(.*/)?"
    s = s.replace("**/", "(.*/)?");
    // Replace remaining "**" with ".*"
    s = s.replace("**", ".*");
    // Replace single "*" (within a segment) with "[^/]*"
    s = s.replace("*", "[^/]*");
    // Replace "?" with single-char wildcard
    s = s.replace("?", ".");
    // Anchor to full resource path
    return "^" + s + "$";
  }
}
