/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import java.util.Map;

/**
 * Engine-internal carrier for a validated replay payload that is ready to be forwarded to the
 * target ingress topic.
 *
 * <p>Produced by {@link DlqReplayProcessor} and consumed by {@link DlqForwardingProcessor}.
 *
 * @param targetTopic full prefixed topic name (e.g. {@code tenant.ns.process-instance})
 * @param key corrected key bytes to write as the Kafka record key
 * @param payload corrected value bytes to write as the Kafka record value
 * @param headers decoded header map (values are raw {@code byte[]}); includes lineage headers and
 *     the fresh engine signature
 */
public record DlqReplayForwardRecord(
    String targetTopic, byte[] key, byte[] payload, Map<String, byte[]> headers) {}
