/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

/** A factory interface for creating TaktParameterResolver instances based on method parameters. */
public interface ResultProcessorFactory {

  /**
   * Creates a ResultProcessor for the given method parameter.
   *
   * @param returnType The result type class to process.
   * @return A ResultProcessor instance.
   */
  ResultProcessor create(Class<?> returnType);
}
