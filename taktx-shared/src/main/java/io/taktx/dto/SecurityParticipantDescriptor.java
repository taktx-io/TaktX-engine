/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.util.Set;

/**
 * Shared participant descriptor used by clients and observability/status telemetry.
 *
 * @param participantId stable participant identity within a namespace
 * @param kind broad participant kind for diagnostics and posture reporting
 * @param capabilities declared participant capabilities; multiple values are allowed
 * @param componentType optional human-readable component label
 */
public record SecurityParticipantDescriptor(
    String participantId,
    ParticipantKind kind,
    Set<ParticipantCapability> capabilities,
    String componentType) {}

