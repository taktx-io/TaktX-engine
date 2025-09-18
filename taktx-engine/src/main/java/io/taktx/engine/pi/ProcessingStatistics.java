/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Getter
@RequiredArgsConstructor
@Slf4j
public class ProcessingStatistics {
  private static final AtomicLong sampleCounter = new AtomicLong();
  private static final int SAMPLE_EVERY_N = 10; // 10% sampling

  private final MeterRegistry meterRegistry;

  // Process instance timing
  private final Map<UUID, Sample> processInstanceTimers = new ConcurrentHashMap<>();
  private final Clock clock;

  // Counters for basic metrics
  private Counter processInstancesStarted;
  private Counter processInstancesFinished;
  private Counter flowNodesStarted;
  private Counter flowNodesContinued;
  private Counter flowNodesFinished;

  // Latency timers with histogram buckets (memory efficient)
  private Timer processInstanceLatency;
  private Timer externalTaskResponseLatency;
  private Timer messageEventLatency;
  private Timer scheduleLatency;
  private Timer processInstanceDuration;

  // Sampling configuration for optimal performance with statistical accuracy
  private static final long MAX_REASONABLE_LATENCY_MS = 300_000; // 5 minutes max

  @PostConstruct
  void init() {
    // Basic counters
    processInstancesStarted = meterRegistry.counter("taktx.process.instances.started");
    processInstancesFinished = meterRegistry.counter("taktx.process.instances.finished");
    flowNodesStarted = meterRegistry.counter("taktx.flow.nodes.started");
    flowNodesContinued = meterRegistry.counter("taktx.flow.nodes.continued");
    flowNodesFinished = meterRegistry.counter("taktx.flow.nodes.finished");

    // Latency timers with histogram buckets for efficient percentile calculation
    // Histogram buckets are memory-efficient, so we can use simple uniform sampling
    processInstanceLatency =
        Timer.builder("taktx.process.instance.latency")
            .description("End-to-end latency for process instance messages")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .distributionStatisticExpiry(Duration.ofMinutes(5)) // 5-minute sliding window
            .distributionStatisticBufferLength(10) // Buffer for sliding window
            .serviceLevelObjectives(
                Duration.ofMillis(10), // Fast
                Duration.ofMillis(50), // Good
                Duration.ofMillis(100), // Acceptable
                Duration.ofMillis(500), // Slow
                Duration.ofSeconds(1), // Very slow
                Duration.ofSeconds(5) // Critical
                )
            .register(meterRegistry);

    externalTaskResponseLatency =
        Timer.builder("taktx.process.externaltaskresponse.latency")
            .description("End-to-end latency for external task response messages")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .distributionStatisticExpiry(Duration.ofMinutes(5)) // 5-minute sliding window
            .distributionStatisticBufferLength(10) // Buffer for sliding window
            .serviceLevelObjectives(
                Duration.ofMillis(10), // Fast
                Duration.ofMillis(50), // Good
                Duration.ofMillis(100), // Acceptable
                Duration.ofMillis(500), // Slow
                Duration.ofSeconds(1), // Very slow
                Duration.ofSeconds(5) // Critical
                )
            .register(meterRegistry);

    messageEventLatency =
        Timer.builder("taktx.message.event.latency")
            .description("End-to-end latency for message event processing")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .distributionStatisticExpiry(Duration.ofMinutes(5)) // 5-minute sliding window
            .distributionStatisticBufferLength(10) // Buffer for sliding window
            .serviceLevelObjectives(
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5))
            .register(meterRegistry);

    scheduleLatency =
        Timer.builder("taktx.schedule.latency")
            .description("End-to-end latency for schedule processing")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .distributionStatisticExpiry(Duration.ofMinutes(5)) // 5-minute sliding window
            .distributionStatisticBufferLength(10) // Buffer for sliding window
            .serviceLevelObjectives(
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5))
            .register(meterRegistry);

    // Simple timer for process instance duration (average only, no histograms)
    processInstanceDuration =
        Timer.builder("taktx.process.instance.duration")
            .description("Average duration of process instances from start to completion")
            .register(meterRegistry);
  }

  /**
   * Record process instance latency with uniform sampling: - Simple 5% sampling for optimal
   * performance - Histogram buckets handle memory efficiency
   */
  public void recordProcessInstanceLatency(long kafkaTimestamp, String processDefinitionKey) {
    recordLatencyWithSampling(
        kafkaTimestamp, processInstanceLatency, "ProcessInstance", processDefinitionKey);
  }

  public void recordExternalTaskResponseLatency(long kafkaTimestamp, String jobId) {
    recordLatencyWithSampling(
        kafkaTimestamp, externalTaskResponseLatency, "ExternalTaskResponse", jobId);
  }

  /** Record message event latency with uniform sampling */
  public void recordMessageEventLatency(long kafkaTimestamp, String messageType) {
    recordLatencyWithSampling(kafkaTimestamp, messageEventLatency, "MessageEvent", messageType);
  }

  /** Record schedule latency with uniform sampling */
  public void recordScheduleLatency(long kafkaTimestamp, String scheduleType) {
    recordLatencyWithSampling(kafkaTimestamp, scheduleLatency, "Schedule", scheduleType);
  }

  private void recordLatencyWithSampling(
      long kafkaTimestamp, Timer timer, String component, String type) {
    if (kafkaTimestamp <= 0) {
      return; // Invalid timestamp
    }

    long currentTime = clock.millis();
    long latencyMs = currentTime - kafkaTimestamp;

    // Skip invalid latencies (negative or extremely high)
    if (latencyMs < 0 || latencyMs > MAX_REASONABLE_LATENCY_MS) {
      if (latencyMs < 0) {
        log.warn(
            "Negative latency detected: {}ms for {} type {} - possible clock skew between nodes",
            latencyMs,
            component,
            type);
      }
      return;
    }

    // Simple uniform sampling - histogram buckets handle memory efficiency

    // In hot path:
    if (sampleCounter.incrementAndGet() % SAMPLE_EVERY_N == 0) {
      timer.record(Duration.ofMillis(latencyMs));
    }
  }

  // Process instance timing methods (for internal duration measurement)
  public void startTimerForProcessInstance(UUID processInstanceId) {
    if (sampleCounter.incrementAndGet() % SAMPLE_EVERY_N == 0) {
      Timer.Sample sample = Timer.start(meterRegistry);
      processInstanceTimers.put(processInstanceId, sample);
    }
  }

  public void stopTimerForProcessInstance(UUID processInstanceId, String processDefinitionKey) {
    Timer.Sample sample = processInstanceTimers.remove(processInstanceId);
    if (sample != null) {
      sample.stop(processInstanceDuration);
    }
  }

  // Counter increment methods
  public void increaseProcessInstancesStarted() {
    processInstancesStarted.increment();
  }

  public void increaseProcessInstancesFinished() {
    processInstancesFinished.increment();
  }

  public void increaseFlowNodesStarted() {
    flowNodesStarted.increment();
  }

  public void increaseFlowNodesFinished() {
    flowNodesFinished.increment();
  }

  public void increaseFlowNodesContinued() {
    flowNodesContinued.increment();
  }
}
