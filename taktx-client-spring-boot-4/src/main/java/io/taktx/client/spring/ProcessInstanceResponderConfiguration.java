/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import io.taktx.client.ProcessInstanceResponder;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a default ProcessInstanceResponder bean for Spring applications. */
@Configuration
public class ProcessInstanceResponderConfiguration {

  /**
   * Provides a default ProcessInstanceResponder bean.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to be used
   * @param processInstanceTriggerEmitter optional injected producer for process-instance trigger
   *     records
   * @return a ProcessInstanceResponder instance
   */
  @Bean
  @ConditionalOnMissingBean
  public ProcessInstanceResponder processInstanceResponder(
      TaktPropertiesHelper taktPropertiesHelper,
      ObjectProvider<KafkaProducer<UUID, ProcessInstanceTriggerDTO>>
          processInstanceTriggerEmitter) {
    return new ProcessInstanceResponder(
        taktPropertiesHelper, processInstanceTriggerEmitter.getIfAvailable());
  }
}
