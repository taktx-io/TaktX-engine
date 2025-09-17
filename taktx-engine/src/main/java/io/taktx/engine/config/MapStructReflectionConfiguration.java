/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
