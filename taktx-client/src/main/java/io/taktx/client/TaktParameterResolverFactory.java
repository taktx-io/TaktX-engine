/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import java.lang.reflect.Parameter;

/** A factory interface for creating TaktParameterResolver instances based on method parameters. */
public interface TaktParameterResolverFactory {

  /**
   * Creates a TaktParameterResolver for the given method parameter.
   *
   * @param parameter The method parameter for which to create a resolver.
   * @return A TaktParameterResolver instance.
   */
  TaktParameterResolver create(Parameter parameter);
}
