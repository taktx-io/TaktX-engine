/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.taktx.client.WorkerBeanInstanceProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/** Quarkus implementation of WorkerBeanInstanceProvider using CDI to obtain bean instances. */
@ApplicationScoped
public class QuarkusBeanInstanceProvider implements WorkerBeanInstanceProvider {

  @Override
  public <T> T getInstance(Class<T> clazz) {
    Instance<T> select = CDI.current().select(clazz);
    return select.get();
  }
}
