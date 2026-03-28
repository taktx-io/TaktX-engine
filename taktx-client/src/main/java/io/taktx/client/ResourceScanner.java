/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
// java
// Add to `build.gradle`:
// implementation 'io.github.classgraph:classgraph:4.8.154'

package io.taktx.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utility class for scanning resources on the classpath or filesystem using Ant-style patterns. */
public final class ResourceScanner {

  private ResourceScanner() {
    // static utility class
  }

  /**
   * Scan the classpath for resources matching the given Ant-style pattern.
   *
   * @param pattern the Ant-style pattern (e.g. "classpath*:com/example/**\/*.xml")
   * @return a list of URIs of matching resources
   */
  public static List<URI> getResources(String pattern) {
    String p = stripClasspathPrefix(pattern);
    String regex = antToRegex(p);
    Pattern compiled = Pattern.compile(regex);

    List<URI> results = new ArrayList<>();
    try (ScanResult scanResult = new ClassGraph().scan()) {

      ResourceList resourcesMatchingPattern = scanResult.getResourcesMatchingPattern(compiled);
      for (Resource resource : resourcesMatchingPattern) {
        results.add(resource.getURI());
      }
    }
    return results;
  }

  /**
   * Scan the filesystem for resources matching the given Ant-style pattern.
   *
   * @param pattern the Ant-style pattern (e.g. "file:/path/to/dir/**\/*.xml")
   * @return a list of Paths of matching resources
   * @throws IOException if an I/O error occurs
   */
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
    int slashBefore = normalizedPattern.lastIndexOf('/', Math.max(firstWildcard, 0));
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
            .replace("]", "\\]");

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
