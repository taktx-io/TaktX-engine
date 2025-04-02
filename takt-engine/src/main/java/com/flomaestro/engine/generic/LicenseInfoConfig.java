/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.generic;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class LicenseInfoConfig {

  void onStart(@Observes StartupEvent ev) {
    String licenseInfo =
        """
            TaktX vX.X.X - Copyright (c) 2025 TaktX B.V. All rights reserved.
            Licensed under TaktX Business Source License V1.0. Free use limited to 3 Kafka partitions.
            See [https://taktx.io/license] for details. For commercial use, contact us at [https://taktx.io/contact].
            """;
    System.out.println(licenseInfo);
  }
}
