/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

@ApplicationScoped
@Getter
public class KafkaClientsConfig {

  @Inject
  @Identifier("default-kafka-broker")
  Map<String, Object> config;

  @Produces
  public AdminClient getAdmin() {
    Map<String, Object> copy = new HashMap<>();
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      if (AdminClientConfig.configNames().contains(entry.getKey())) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    return KafkaAdminClient.create(copy);
  }
}
