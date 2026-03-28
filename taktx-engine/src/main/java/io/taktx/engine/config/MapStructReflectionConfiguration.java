/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Configuration to ensure MapStruct generated implementation classes are available for reflection
 * in GraalVM native image builds.
 */
@RegisterForReflection(
    targets = {
      io.taktx.engine.pi.ProcessInstanceMapperImpl.class,
      io.taktx.engine.pi.DtoMapperImpl.class
    })
public class MapStructReflectionConfiguration {}
