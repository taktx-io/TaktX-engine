/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.auth;

import jakarta.annotation.Nullable;

/** Supplies JWT authorization tokens for outbound TaktX client commands. */
@FunctionalInterface
public interface AuthorizationTokenProvider {

  /**
   * Returns the JWT to attach to the given command request.
   *
   * @param request command context for which a token is requested
   * @return compact JWT string, or {@code null} when no token is available
   */
  @Nullable
  String getAuthorizationToken(CommandAuthorizationRequest request);
}
