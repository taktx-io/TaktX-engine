/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.SignalSerializer;
import io.taktx.dto.SignalDTO;
import io.taktx.security.SigningServiceHolder.SigningFunction;
import io.taktx.util.TaktPropertiesHelper;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * A sender for message events, responsible for producing and sending MessageEventDTO objects to a
 * Kafka topic.
 */
public class SignalSender implements AutoCloseable {

  private final Producer<String, SignalDTO> signalEmitter;
  private final TaktPropertiesHelper taktPropertiesHelper;
  private volatile Runnable beforeSendHook = () -> {};
  private volatile ProtectedClientDataPlaneGuard protectedDataPlaneGuard =
      ProtectedClientDataPlaneGuard.noop();

  /**
   * Constructor for MessageEventSender.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  public SignalSender(TaktPropertiesHelper taktPropertiesHelper) {
    this(taktPropertiesHelper, null);
  }

  public SignalSender(
      TaktPropertiesHelper taktPropertiesHelper,
      Supplier<SigningFunction> signingFunctionSupplier) {
    this(
        taktPropertiesHelper,
        signingFunctionSupplier,
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(),
            new StringSerializer(),
            new SignalSerializer(signingFunctionSupplier)));
  }

  SignalSender(
      TaktPropertiesHelper taktPropertiesHelper,
      Supplier<SigningFunction> signingFunctionSupplier,
      Producer<String, SignalDTO> signalEmitter) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.signalEmitter = signalEmitter;
  }

  void setProtectedDataPlaneGuard(
      @jakarta.annotation.Nullable ProtectedClientDataPlaneGuard protectedDataPlaneGuard) {
    this.protectedDataPlaneGuard =
        protectedDataPlaneGuard != null
            ? protectedDataPlaneGuard
            : ProtectedClientDataPlaneGuard.noop();
  }

  void setBeforeSendHook(@jakarta.annotation.Nullable Runnable beforeSendHook) {
    this.beforeSendHook = beforeSendHook != null ? beforeSendHook : () -> {};
  }

  /**
   * Sends a signal to the configured Kafka topic.
   *
   * @param signalDTO the SignalDTO to send
   */
  public void sendMSignal(SignalDTO signalDTO) {
    protectedDataPlaneGuard.check(ProtectedClientDataPlaneOperation.SIGNAL_EVENT, null);
    beforeSendHook.run();
    signalEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNAL_TOPIC.getTopicName()),
            signalDTO.getSignalName(),
            signalDTO));
  }

  @Override
  public void close() {
    signalEmitter.close();
  }
}
