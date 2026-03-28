/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
