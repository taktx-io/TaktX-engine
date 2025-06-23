/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserTaskResponseResultDTO {
  private UserTaskResponseType responseType;

  private String code;

  private String message;

  public UserTaskResponseResultDTO(UserTaskResponseType responseType, String code, String message) {
    this.responseType = responseType;
    this.code = code;
    this.message = message;
  }
}
