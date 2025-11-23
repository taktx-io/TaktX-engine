/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

/**
 * Default implementation of TaktParameterResolverFactory that creates parameter resolvers based on
 * parameter types and annotations.
 */
public class DefaultResultProcessorFactory implements ResultProcessorFactory {

  @Override
  public ResultProcessor create(Class<?> returnTypeClass) {
    boolean methodIsVoid = returnTypeClass.equals(Void.TYPE);
    if (methodIsVoid) {
      return new VoidResultProcessor();
    } else {
      return new ObjectResultProcessor();
    }
  }
}
