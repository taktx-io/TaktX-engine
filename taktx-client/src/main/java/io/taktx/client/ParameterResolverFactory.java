/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import java.lang.reflect.Parameter;

/** A factory interface for creating TaktParameterResolver instances based on method parameters. */
public interface ParameterResolverFactory {

  /**
   * Creates a TaktParameterResolver for the given method parameter.
   *
   * @param parameter The method parameter for which to create a resolver.
   * @return A TaktParameterResolver instance.
   */
  ParameterResolver create(Parameter parameter);
}
