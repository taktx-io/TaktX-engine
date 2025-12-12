/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as a TaktX worker method with a specified task ID and auto-completion
 * option.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface JobWorker {
  /**
   * The task ID associated with the worker method.
   *
   * @return The task ID associated with the worker method.
   */
  String taskId();

  /**
   * Whether the task should be auto-completed after execution. Default is true.
   *
   * @return True if the task should be auto-completed, false otherwise.
   */
  boolean autoComplete() default true;

  /** The acknowledgement strategy for Kafka messages. Default is EXPLICIT_BATCH. */
  AckStrategy ackStrategy() default AckStrategy.EXPLICIT_BATCH;

  /** The threading strategy for message processing. Default is VIRTUAL_THREAD_WAIT. */
  ThreadingStrategy threadingStrategy() default ThreadingStrategy.VIRTUAL_THREAD_WAIT;
}
