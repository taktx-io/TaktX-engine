/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

public interface Mapper {

  default Set<String> mapQNameList(List<QName> incoming) {
    return incoming.stream().map(QName::getLocalPart).collect(Collectors.toSet());
  }
}
