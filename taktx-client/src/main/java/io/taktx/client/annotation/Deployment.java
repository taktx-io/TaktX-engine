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

/**
 * Marks a class whose referenced BPMN resources should be deployed automatically at client startup.
 *
 * <p>Provide one or more resource paths (classpath or filesystem). Example resource values:
 * "classpath:demoProcess.bpmn" or "classpath:processes/*.bpmn".
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.TYPE})
public @interface Deployment {
  /**
   * The resource paths for the deployment. Supports multiple values and classpath wildcards.
   *
   * @return one or more resource location strings (for example: "classpath:process.bpmn")
   */
  String[] resources();
}
