/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDecisionTableDTO;
import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionStateEnum;
import io.taktx.dto.DmnDefinitionsKey;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.DmnInputClauseDTO;
import io.taktx.dto.DmnLiteralExpressionDTO;
import io.taktx.dto.DmnOutputClauseDTO;
import io.taktx.dto.DmnRuleDTO;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.proto.DmnDecisionMessage;
import io.taktx.proto.DmnDecisionTableMessage;
import io.taktx.proto.DmnDefinitionMessage;
import io.taktx.proto.DmnDefinitionsKeyMessage;
import io.taktx.proto.DmnInputClauseMessage;
import io.taktx.proto.DmnLiteralExpressionMessage;
import io.taktx.proto.DmnOutputClauseMessage;
import io.taktx.proto.DmnRuleMessage;
import io.taktx.proto.ParsedDmnDefinitionsMessage;
import io.taktx.proto.XmlDmnDefinitionsMessage;
import java.util.List;

/** Shared DTO ↔ protobuf mapper for DMN definition records. */
public final class DmnDefinitionsProtoMapper {

  private DmnDefinitionsProtoMapper() {}

  public static DmnDefinitionMessage toProto(DmnDefinitionDTO dto) {
    DmnDefinitionMessage.Builder builder = DmnDefinitionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDefinitions() != null) {
      builder.setDefinitions(toProto(dto.getDefinitions()));
    }
    if (dto.getVersion() != null) {
      builder.setVersion(dto.getVersion());
    }
    if (dto.getState() != null) {
      builder.setState(toProto(dto.getState()));
    }
    return builder.build();
  }

  public static DmnDefinitionDTO toDto(DmnDefinitionMessage message) {
    if (message == null) {
      return null;
    }
    return new DmnDefinitionDTO(
        message.hasDefinitions() ? toDto(message.getDefinitions()) : null,
        message.getVersion(),
        toDto(message.getState()));
  }

  public static XmlDmnDefinitionsMessage toProto(XmlDmnDefinitionsDTO dto) {
    XmlDmnDefinitionsMessage.Builder builder = XmlDmnDefinitionsMessage.newBuilder();
    if (dto != null && dto.getXml() != null) {
      builder.setXml(dto.getXml());
    }
    return builder.build();
  }

  public static XmlDmnDefinitionsDTO toDto(XmlDmnDefinitionsMessage message) {
    return new XmlDmnDefinitionsDTO(emptyToNull(message.getXml()));
  }

  public static ParsedDmnDefinitionsMessage toProto(ParsedDmnDefinitionsDTO dto) {
    ParsedDmnDefinitionsMessage.Builder builder = ParsedDmnDefinitionsMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDefinitionsKey() != null) {
      builder.setDefinitionsKey(toProto(dto.getDefinitionsKey()));
    }
    if (dto.getName() != null) {
      builder.setName(dto.getName());
    }
    if (dto.getDecisions() != null) {
      dto.getDecisions().stream()
          .map(DmnDefinitionsProtoMapper::toProto)
          .forEach(builder::addDecisions);
    }
    return builder.build();
  }

  public static ParsedDmnDefinitionsDTO toDto(ParsedDmnDefinitionsMessage message) {
    if (message == null) {
      return null;
    }
    return ParsedDmnDefinitionsDTO.builder()
        .definitionsKey(message.hasDefinitionsKey() ? toDto(message.getDefinitionsKey()) : null)
        .name(emptyToNull(message.getName()))
        .decisions(
            message.getDecisionsList().stream().map(DmnDefinitionsProtoMapper::toDto).toList())
        .build();
  }

  private static DmnDefinitionsKeyMessage toProto(DmnDefinitionsKey key) {
    DmnDefinitionsKeyMessage.Builder builder = DmnDefinitionsKeyMessage.newBuilder();
    if (key != null) {
      if (key.getDmnDefinitionId() != null) {
        builder.setDmnDefinitionId(key.getDmnDefinitionId());
      }
      if (key.getHash() != null) {
        builder.setHash(key.getHash());
      }
    }
    return builder.build();
  }

  private static DmnDefinitionsKey toDto(DmnDefinitionsKeyMessage message) {
    return new DmnDefinitionsKey(
        emptyToNull(message.getDmnDefinitionId()), emptyToNull(message.getHash()));
  }

  private static DmnDecisionMessage toProto(DmnDecisionDTO dto) {
    DmnDecisionMessage.Builder builder = DmnDecisionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getName() != null) {
      builder.setName(dto.getName());
    }
    if (dto.getDecisionTable() != null) {
      builder.setDecisionTable(toProto(dto.getDecisionTable()));
    }
    if (dto.getLiteralExpression() != null) {
      builder.setLiteralExpression(toProto(dto.getLiteralExpression()));
    }
    List<String> requiredDecisionIds = dto.getRequiredDecisionIds();
    if (requiredDecisionIds != null) {
      builder.addAllRequiredDecisionIds(requiredDecisionIds);
    }
    return builder.build();
  }

  private static DmnDecisionDTO toDto(DmnDecisionMessage message) {
    return new DmnDecisionDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getName()),
        message.hasDecisionTable() ? toDto(message.getDecisionTable()) : null,
        message.hasLiteralExpression() ? toDto(message.getLiteralExpression()) : null,
        List.copyOf(message.getRequiredDecisionIdsList()));
  }

  private static DmnDecisionTableMessage toProto(DmnDecisionTableDTO dto) {
    DmnDecisionTableMessage.Builder builder = DmnDecisionTableMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getHitPolicy() != null) {
      builder.setHitPolicy(toProto(dto.getHitPolicy()));
    }
    if (dto.getCollectOperator() != null) {
      builder.setCollectOperator(toProto(dto.getCollectOperator()));
    }
    if (dto.getInputs() != null) {
      dto.getInputs().stream().map(DmnDefinitionsProtoMapper::toProto).forEach(builder::addInputs);
    }
    if (dto.getOutputs() != null) {
      dto.getOutputs().stream()
          .map(DmnDefinitionsProtoMapper::toProto)
          .forEach(builder::addOutputs);
    }
    if (dto.getRules() != null) {
      dto.getRules().stream().map(DmnDefinitionsProtoMapper::toProto).forEach(builder::addRules);
    }
    return builder.build();
  }

  private static DmnDecisionTableDTO toDto(DmnDecisionTableMessage message) {
    return new DmnDecisionTableDTO(
        emptyToNull(message.getId()),
        toDto(message.getHitPolicy()),
        toDto(message.getCollectOperator()),
        message.getInputsList().stream().map(DmnDefinitionsProtoMapper::toDto).toList(),
        message.getOutputsList().stream().map(DmnDefinitionsProtoMapper::toDto).toList(),
        message.getRulesList().stream().map(DmnDefinitionsProtoMapper::toDto).toList());
  }

  private static DmnLiteralExpressionMessage toProto(DmnLiteralExpressionDTO dto) {
    DmnLiteralExpressionMessage.Builder builder = DmnLiteralExpressionMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getExpression() != null) {
      builder.setExpression(dto.getExpression());
    }
    if (dto.getTypeRef() != null) {
      builder.setTypeRef(dto.getTypeRef());
    }
    return builder.build();
  }

  private static DmnLiteralExpressionDTO toDto(DmnLiteralExpressionMessage message) {
    return new DmnLiteralExpressionDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getExpression()),
        emptyToNull(message.getTypeRef()));
  }

  private static DmnInputClauseMessage toProto(DmnInputClauseDTO dto) {
    DmnInputClauseMessage.Builder builder = DmnInputClauseMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getLabel() != null) {
      builder.setLabel(dto.getLabel());
    }
    if (dto.getInputExpression() != null) {
      builder.setInputExpression(dto.getInputExpression());
    }
    if (dto.getTypeRef() != null) {
      builder.setTypeRef(dto.getTypeRef());
    }
    return builder.build();
  }

  private static DmnInputClauseDTO toDto(DmnInputClauseMessage message) {
    return new DmnInputClauseDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getLabel()),
        emptyToNull(message.getInputExpression()),
        emptyToNull(message.getTypeRef()));
  }

  private static DmnOutputClauseMessage toProto(DmnOutputClauseDTO dto) {
    DmnOutputClauseMessage.Builder builder = DmnOutputClauseMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getLabel() != null) {
      builder.setLabel(dto.getLabel());
    }
    if (dto.getName() != null) {
      builder.setName(dto.getName());
    }
    if (dto.getTypeRef() != null) {
      builder.setTypeRef(dto.getTypeRef());
    }
    return builder.build();
  }

  private static DmnOutputClauseDTO toDto(DmnOutputClauseMessage message) {
    return new DmnOutputClauseDTO(
        emptyToNull(message.getId()),
        emptyToNull(message.getLabel()),
        emptyToNull(message.getName()),
        emptyToNull(message.getTypeRef()));
  }

  private static DmnRuleMessage toProto(DmnRuleDTO dto) {
    DmnRuleMessage.Builder builder = DmnRuleMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getId() != null) {
      builder.setId(dto.getId());
    }
    if (dto.getInputEntries() != null) {
      builder.addAllInputEntries(dto.getInputEntries());
    }
    if (dto.getOutputEntries() != null) {
      builder.addAllOutputEntries(dto.getOutputEntries());
    }
    return builder.build();
  }

  private static DmnRuleDTO toDto(DmnRuleMessage message) {
    return new DmnRuleDTO(
        emptyToNull(message.getId()),
        List.copyOf(message.getInputEntriesList()),
        List.copyOf(message.getOutputEntriesList()));
  }

  private static io.taktx.proto.DmnDefinitionStateEnum toProto(DmnDefinitionStateEnum state) {
    return switch (state) {
      case INACTIVE -> io.taktx.proto.DmnDefinitionStateEnum.DMN_DEFINITION_STATE_INACTIVE;
      case ACTIVE -> io.taktx.proto.DmnDefinitionStateEnum.DMN_DEFINITION_STATE_ACTIVE;
    };
  }

  private static DmnDefinitionStateEnum toDto(io.taktx.proto.DmnDefinitionStateEnum state) {
    return switch (state) {
      case DMN_DEFINITION_STATE_INACTIVE -> DmnDefinitionStateEnum.INACTIVE;
      case DMN_DEFINITION_STATE_UNSPECIFIED, DMN_DEFINITION_STATE_ACTIVE, UNRECOGNIZED ->
          DmnDefinitionStateEnum.ACTIVE;
    };
  }

  private static io.taktx.proto.DmnHitPolicy toProto(DmnHitPolicy hitPolicy) {
    return switch (hitPolicy) {
      case FIRST -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_FIRST;
      case ANY -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_ANY;
      case COLLECT -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_COLLECT;
      case RULE_ORDER -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_RULE_ORDER;
      case OUTPUT_ORDER -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_OUTPUT_ORDER;
      case PRIORITY -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_PRIORITY;
      case UNIQUE -> io.taktx.proto.DmnHitPolicy.DMN_HIT_POLICY_UNIQUE;
    };
  }

  private static DmnHitPolicy toDto(io.taktx.proto.DmnHitPolicy hitPolicy) {
    return switch (hitPolicy) {
      case DMN_HIT_POLICY_FIRST -> DmnHitPolicy.FIRST;
      case DMN_HIT_POLICY_ANY -> DmnHitPolicy.ANY;
      case DMN_HIT_POLICY_COLLECT -> DmnHitPolicy.COLLECT;
      case DMN_HIT_POLICY_RULE_ORDER -> DmnHitPolicy.RULE_ORDER;
      case DMN_HIT_POLICY_OUTPUT_ORDER -> DmnHitPolicy.OUTPUT_ORDER;
      case DMN_HIT_POLICY_PRIORITY -> DmnHitPolicy.PRIORITY;
      case DMN_HIT_POLICY_UNSPECIFIED, DMN_HIT_POLICY_UNIQUE, UNRECOGNIZED -> DmnHitPolicy.UNIQUE;
    };
  }

  private static io.taktx.proto.DmnCollectOperator toProto(DmnCollectOperator collectOperator) {
    return switch (collectOperator) {
      case SUM -> io.taktx.proto.DmnCollectOperator.DMN_COLLECT_SUM;
      case MIN -> io.taktx.proto.DmnCollectOperator.DMN_COLLECT_MIN;
      case MAX -> io.taktx.proto.DmnCollectOperator.DMN_COLLECT_MAX;
      case COUNT -> io.taktx.proto.DmnCollectOperator.DMN_COLLECT_COUNT;
      case NONE -> io.taktx.proto.DmnCollectOperator.DMN_COLLECT_NONE;
    };
  }

  private static DmnCollectOperator toDto(io.taktx.proto.DmnCollectOperator collectOperator) {
    return switch (collectOperator) {
      case DMN_COLLECT_SUM -> DmnCollectOperator.SUM;
      case DMN_COLLECT_MIN -> DmnCollectOperator.MIN;
      case DMN_COLLECT_MAX -> DmnCollectOperator.MAX;
      case DMN_COLLECT_COUNT -> DmnCollectOperator.COUNT;
      case DMN_COLLECT_UNSPECIFIED, DMN_COLLECT_NONE, UNRECOGNIZED -> DmnCollectOperator.NONE;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
