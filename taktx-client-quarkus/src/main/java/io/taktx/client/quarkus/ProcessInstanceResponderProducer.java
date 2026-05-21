/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import io.quarkus.arc.DefaultBean;
import io.taktx.client.ProcessInstanceResponder;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;

/** Produces a default ProcessInstanceResponder bean for Quarkus applications. */
@ApplicationScoped
public class ProcessInstanceResponderProducer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Instance<KafkaProducer<UUID, ProcessInstanceTriggerDTO>>
      processInstanceTriggerEmitter;

  /**
   * Constructor injecting the TaktPropertiesHelper.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to be used
   * @param processInstanceTriggerEmitter optional injected producer for process-instance trigger
   *     records
   */
  public ProcessInstanceResponderProducer(
      TaktPropertiesHelper taktPropertiesHelper,
      Instance<KafkaProducer<UUID, ProcessInstanceTriggerDTO>> processInstanceTriggerEmitter) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.processInstanceTriggerEmitter = processInstanceTriggerEmitter;
  }

  /**
   * Produces a default ProcessInstanceResponder bean.
   *
   * @return a ProcessInstanceResponder instance
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  public ProcessInstanceResponder processInstanceResponder() {
    KafkaProducer<UUID, ProcessInstanceTriggerDTO> producer =
        processInstanceTriggerEmitter != null
                && !processInstanceTriggerEmitter.isUnsatisfied()
                && !processInstanceTriggerEmitter.isAmbiguous()
            ? processInstanceTriggerEmitter.get()
            : null;
    return new ProcessInstanceResponder(taktPropertiesHelper, producer);
  }
}
