/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.FixedRateMessageScheduleDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.RecurringMessageScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.proto.FixedRateMessageScheduleMessage;
import io.taktx.proto.MessageScheduleEnvelope;
import io.taktx.proto.OneTimeScheduleMessage;
import io.taktx.proto.RecurringMessageScheduleMessage;
import io.taktx.proto.SchedulableMessageEnvelope;

/** Shared DTO ↔ protobuf mapper for scheduled message records. */
public final class MessageScheduleProtoMapper {

  private MessageScheduleProtoMapper() {}

  public static MessageScheduleEnvelope toProto(MessageScheduleDTO dto) {
    MessageScheduleEnvelope.Builder builder = MessageScheduleEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto instanceof OneTimeScheduleDTO oneTimeSchedule) {
      builder.setOneTime(toProto(oneTimeSchedule));
    } else if (dto instanceof FixedRateMessageScheduleDTO fixedRateSchedule) {
      builder.setFixedRate(toProto(fixedRateSchedule));
    } else if (dto instanceof RecurringMessageScheduleDTO recurringSchedule) {
      builder.setRecurring(toProto(recurringSchedule));
    } else {
      throw new IllegalArgumentException(
          "Unsupported message schedule type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  public static MessageScheduleDTO toDto(MessageScheduleEnvelope envelope) {
    if (envelope == null) {
      return null;
    }
    return switch (envelope.getScheduleCase()) {
      case ONE_TIME -> toDto(envelope.getOneTime());
      case FIXED_RATE -> toDto(envelope.getFixedRate());
      case RECURRING -> toDto(envelope.getRecurring());
      case SCHEDULE_NOT_SET -> null;
    };
  }

  private static OneTimeScheduleMessage toProto(OneTimeScheduleDTO dto) {
    OneTimeScheduleMessage.Builder builder = OneTimeScheduleMessage.newBuilder();
    if (dto.getMessage() != null) {
      builder.setMessage(toProto(dto.getMessage()));
    }
    builder.setInstantiationTime(dto.getInstantiationTime());
    builder.setWhen(dto.getWhen());
    return builder.build();
  }

  private static FixedRateMessageScheduleMessage toProto(FixedRateMessageScheduleDTO dto) {
    FixedRateMessageScheduleMessage.Builder builder = FixedRateMessageScheduleMessage.newBuilder();
    if (dto.getMessage() != null) {
      builder.setMessage(toProto(dto.getMessage()));
    }
    builder.setInstantiationTime(dto.getInstantiationTime());
    builder.setPeriod(dto.getPeriod());
    builder.setRepetitions(dto.getRepetitions());
    return builder.build();
  }

  private static RecurringMessageScheduleMessage toProto(RecurringMessageScheduleDTO dto) {
    RecurringMessageScheduleMessage.Builder builder = RecurringMessageScheduleMessage.newBuilder();
    if (dto.getMessage() != null) {
      builder.setMessage(toProto(dto.getMessage()));
    }
    builder.setInstantiationTime(dto.getInstantiationTime());
    if (dto.getCron() != null) {
      builder.setCron(dto.getCron());
    }
    return builder.build();
  }

  private static MessageScheduleDTO toDto(OneTimeScheduleMessage message) {
    return new OneTimeScheduleDTO(
        message.hasMessage() ? toDto(message.getMessage()) : null,
        message.getInstantiationTime(),
        message.getWhen());
  }

  private static MessageScheduleDTO toDto(FixedRateMessageScheduleMessage message) {
    return new FixedRateMessageScheduleDTO(
        message.hasMessage() ? toDto(message.getMessage()) : null,
        message.getPeriod(),
        message.getRepetitions(),
        message.getInstantiationTime());
  }

  private static MessageScheduleDTO toDto(RecurringMessageScheduleMessage message) {
    return new RecurringMessageScheduleDTO(
        message.hasMessage() ? toDto(message.getMessage()) : null,
        emptyToNull(message.getCron()),
        message.getInstantiationTime());
  }

  private static SchedulableMessageEnvelope toProto(SchedulableMessageDTO dto) {
    SchedulableMessageEnvelope.Builder builder = SchedulableMessageEnvelope.newBuilder();
    if (dto instanceof ProcessInstanceTriggerDTO processInstanceTrigger) {
      builder.setProcessInstanceTrigger(
          ProcessInstanceTriggerProtoMapper.toProto(processInstanceTrigger));
    } else if (dto instanceof ExternalTaskTriggerDTO externalTaskTrigger) {
      builder.setExternalTaskTrigger(WorkerTriggerProtoMapper.toProto(externalTaskTrigger));
    } else if (dto != null) {
      throw new IllegalArgumentException(
          "Unsupported schedulable message type: " + dto.getClass().getName());
    }
    return builder.build();
  }

  private static SchedulableMessageDTO toDto(SchedulableMessageEnvelope message) {
    return switch (message.getMessageCase()) {
      case PROCESS_INSTANCE_TRIGGER ->
          ProcessInstanceTriggerProtoMapper.toDto(message.getProcessInstanceTrigger());
      case EXTERNAL_TASK_TRIGGER ->
          WorkerTriggerProtoMapper.toDto(message.getExternalTaskTrigger());
      case MESSAGE_NOT_SET -> null;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
