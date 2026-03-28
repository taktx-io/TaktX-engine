/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
