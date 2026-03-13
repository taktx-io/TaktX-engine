/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.serdes;

import jakarta.annotation.Nullable;

/**
 * Wraps the outcome of a fault-tolerant deserialization attempt.
 *
 * <p>Unlike a plain Kafka {@link org.apache.kafka.common.serialization.Deserializer} that throws on
 * failure, this type allows the consumer to receive both the (possibly partially valid) payload and
 * any error that occurred — most commonly a signature-verification failure where the body itself
 * was decoded successfully.
 *
 * <ul>
 *   <li>{@link #isSuccess()} — body decoded <em>and</em> signature verified (or no verification
 *       required).
 *   <li>{@link #hasValue()} — body was decoded even if signature verification failed. The consumer
 *       can use the value to identify the task/process instance and report a BPMN error back.
 *   <li>{@link #getError()} — non-null when anything went wrong. May carry a signature error even
 *       though {@link #hasValue()} is {@code true}.
 * </ul>
 *
 * @param <T> the deserialized payload type
 */
public final class DeserializationResult<T> {

  private final T value;
  private final String error;

  private DeserializationResult(T value, String error) {
    this.value = value;
    this.error = error;
  }

  /** Creates a fully successful result — body decoded, signature verified. */
  public static <T> DeserializationResult<T> success(T value) {
    return new DeserializationResult<>(value, null);
  }

  /**
   * Creates a result where the body was decoded but an error occurred (e.g. signature failure). The
   * value is still available so the consumer can identify the task and report back.
   */
  public static <T> DeserializationResult<T> bodyDecodedWithError(T value, String error) {
    return new DeserializationResult<>(value, error);
  }

  /** Creates a result where the body could not be decoded at all. */
  public static <T> DeserializationResult<T> failure(String error) {
    return new DeserializationResult<>(null, error);
  }

  /**
   * Returns {@code true} if deserialization and (when applicable) signature verification both
   * succeeded.
   */
  public boolean isSuccess() {
    return error == null;
  }

  /** Returns {@code true} if the body payload was decoded, regardless of signature status. */
  public boolean hasValue() {
    return value != null;
  }

  /**
   * Returns the deserialized value, or {@code null} if the body could not be decoded. Always check
   * {@link #isSuccess()} or {@link #getError()} before trusting the value.
   */
  @Nullable
  public T getValue() {
    return value;
  }

  /** Returns the error message, or {@code null} if everything succeeded. */
  @Nullable
  public String getError() {
    return error;
  }

  @Override
  public String toString() {
    return isSuccess()
        ? "DeserializationResult{ok, value=" + value + "}"
        : "DeserializationResult{error='" + error + "', hasValue=" + hasValue() + "}";
  }
}
