package io.taktx;

import lombok.Getter;

@Getter
public enum CleanupPolicy {
  DELETE("delete"),
  COMPACT("compact");

  private final String kafkaPolicyValue;

  CleanupPolicy(String kafkaPolicyValue) {
    this.kafkaPolicyValue = kafkaPolicyValue;
  }
}
