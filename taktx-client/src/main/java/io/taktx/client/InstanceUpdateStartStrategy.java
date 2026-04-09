/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

/**
 * Controls where a {@link ProcessInstanceUpdateConsumer} begins reading from the instance-update
 * topic when it starts.
 *
 * <p>Use {@link #RESUME} (the default) for normal operation. Use {@link #EARLIEST} when a consumer
 * needs a guaranteed full-history replay — for example an ingester that rebuilds its projection
 * from scratch. {@code auto.offset.reset=earliest} alone is insufficient once a consumer group has
 * previously committed offsets; an explicit {@code seekToBeginning()} call after partition
 * assignment is required, which is what this strategy arranges.
 */
public enum InstanceUpdateStartStrategy {

  /**
   * Resume from the last committed offset for this consumer group. This is the default behaviour
   * and is appropriate for live consumers that must not re-process already-handled records.
   */
  RESUME,

  /**
   * Seek to offset 0 on every assigned partition immediately after the initial rebalance,
   * regardless of any previously committed offsets. Use this when the consumer must replay the full
   * topic history from the beginning (e.g. a cold-start projection rebuild).
   */
  EARLIEST
}
