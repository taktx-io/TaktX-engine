/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.annotation;

/** Defines how worker methods are executed after a task is dispatched to client code. */
public enum ThreadingStrategy {
  /** Execute work on the polling thread and wait synchronously for completion. */
  SINGLE_THREAD,
  /** Execute work on a virtual thread and wait for completion before acknowledging the record. */
  VIRTUAL_THREAD_WAIT,
  /** Execute work on a virtual thread and return immediately without waiting for completion. */
  VIRTUAL_THREAD_FIRE_AND_FORGET
}
