/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import java.time.Duration;

/** Exception to indicate a promise with a specified duration. */
public class TaktXBpmnPromise extends RuntimeException {

  private final Duration duration;

  /**
   * Constructor for TaktXBpmnPromise.
   *
   * @param duration the duration of the promise
   */
  public TaktXBpmnPromise(Duration duration) {
    this.duration = duration;
  }

  /**
   * Get the duration of the promise.
   *
   * @return the duration
   */
  public Duration getDuration() {
    return duration;
  }
}
