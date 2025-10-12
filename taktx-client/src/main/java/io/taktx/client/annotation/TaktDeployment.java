/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Annotation to mark a class for TaktX deployment with a specified resource. */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.TYPE})
public @interface TaktDeployment {
  /**
   * The resource path for the deployment.
   *
   * @return The resource path for the deployment.
   */
  String resource();
}
