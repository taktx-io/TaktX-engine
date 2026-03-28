/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
  String type();

  /**
   * Whether the task should be auto-completed after execution. Default is true.
   *
   * @return True if the task should be auto-completed, false otherwise.
   */
  boolean autoComplete() default true;

  /**
   * The acknowledgement strategy for Kafka messages. Default is EXPLICIT_BATCH.
   *
   * @return The acknowledgement strategy for Kafka messages.
   */
  AckStrategy ackStrategy() default AckStrategy.EXPLICIT_BATCH;

  /**
   * The threading strategy for message processing. Default is VIRTUAL_THREAD_WAIT.
   *
   * @return The threading strategy for message processing.
   */
  ThreadingStrategy threadingStrategy() default ThreadingStrategy.VIRTUAL_THREAD_WAIT;
}
