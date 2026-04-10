/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a class whose referenced BPMN and/or DMN resources should be deployed automatically at
 * client startup.
 *
 * <p>Provide one or more resource paths (classpath or filesystem). Example resource values:
 * "classpath:demoProcess.bpmn", "classpath:processes/*.bpmn", "classpath:dmn/discount.dmn".
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.TYPE})
public @interface Deployment {
  /**
   * The BPMN resource paths for the deployment. Supports multiple values and classpath wildcards.
   *
   * @return one or more resource location strings (for example: "classpath:process.bpmn")
   */
  String[] resources() default {};

  /**
   * The DMN resource paths for the deployment. Supports multiple values and classpath wildcards.
   *
   * @return one or more DMN resource location strings (for example: "classpath:dmn/discount.dmn")
   */
  String[] dmnResources() default {};
}
