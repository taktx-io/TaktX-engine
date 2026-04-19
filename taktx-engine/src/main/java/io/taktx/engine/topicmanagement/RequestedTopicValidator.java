/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import io.taktx.dto.Constants;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class RequestedTopicValidator {

  private final TaktConfiguration taktConfiguration;

  RequestedTopicValidationResult validate(String recordKey, TopicMetaDTO topicMeta) {
    String effectiveTopicName =
        firstNonBlank(recordKey, topicMeta == null ? null : topicMeta.getTopicName());

    if (topicMeta == null) {
      return RequestedTopicValidationResult.rejected(
          effectiveTopicName, "topic metadata payload is required");
    }
    if (isBlank(recordKey)) {
      return RequestedTopicValidationResult.rejected(
          effectiveTopicName, "request key must contain the fully-qualified topic name");
    }
    if (isBlank(topicMeta.getTopicName())) {
      return RequestedTopicValidationResult.rejected(
          recordKey, "TopicMetaDTO.topicName must not be blank");
    }
    if (!recordKey.equals(topicMeta.getTopicName())) {
      return RequestedTopicValidationResult.rejected(
          recordKey, "request key must exactly match TopicMetaDTO.topicName");
    }
    if (!recordKey.startsWith(localTopicPrefix())) {
      return RequestedTopicValidationResult.rejected(
          recordKey,
          "requested topic must stay within the local tenant/namespace prefix '"
              + localTopicPrefix()
              + "'");
    }
    if (!recordKey.startsWith(allowedDynamicTopicPrefix())) {
      return RequestedTopicValidationResult.rejected(
          recordKey,
          "only local external-task trigger topics may be created via topic-meta-requested");
    }
    if (recordKey.length() <= allowedDynamicTopicPrefix().length()) {
      return RequestedTopicValidationResult.rejected(
          recordKey, "external-task trigger topic suffix must not be blank");
    }
    return RequestedTopicValidationResult.accepted(recordKey);
  }

  boolean isAllowedRequestedTopicName(String topicName) {
    return !isBlank(topicName)
        && topicName.startsWith(allowedDynamicTopicPrefix())
        && topicName.length() > allowedDynamicTopicPrefix().length();
  }

  private String localTopicPrefix() {
    return taktConfiguration.getPrefixed("");
  }

  private String allowedDynamicTopicPrefix() {
    return taktConfiguration.getPrefixed(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX);
  }

  private static String firstNonBlank(String first, String second) {
    if (!isBlank(first)) {
      return first;
    }
    return isBlank(second) ? null : second;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

record RequestedTopicValidationResult(boolean valid, String topicName, String rejectionReason) {

  static RequestedTopicValidationResult accepted(String topicName) {
    return new RequestedTopicValidationResult(true, topicName, null);
  }

  static RequestedTopicValidationResult rejected(String topicName, String rejectionReason) {
    return new RequestedTopicValidationResult(false, topicName, rejectionReason);
  }
}
