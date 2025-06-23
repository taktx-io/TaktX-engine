/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.Script;
import io.taktx.bpmn.TScriptTask;
import io.taktx.bpmn.TaskDefinition;
import io.taktx.bpmn.TaskHeaders;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ScriptTaskDTO;
import io.taktx.dto.ScriptType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZeebeScriptTaskMapper implements ScriptTaskMapper {
  String DEFAULT_RETRIES = "3";

  @Override
  public ScriptTaskDTO map(
      TScriptTask scriptTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    ScriptType scriptType = ScriptType.UNKNOWN;

    Optional<TaskDefinition> taskDefinition =
        ExtensionElementHelper.extractExtensionElement(
            scriptTask.getExtensionElements(), TaskDefinition.class);
    String taskDefinitionId = scriptTask.getId();
    String retries = DEFAULT_RETRIES;
    String scriptExpression = "";
    String resultVariableName = "";
    if (taskDefinition.isPresent()) {
      scriptType = ScriptType.JOBWORKER;
      taskDefinitionId = taskDefinition.get().getType();
      retries = taskDefinition.get().getRetries();
    } else {
      Optional<Script> scriptElement =
          ExtensionElementHelper.extractExtensionElement(
              scriptTask.getExtensionElements(), Script.class);

      if (scriptElement.isPresent()) {
        scriptType = ScriptType.FEEL;
        scriptExpression = scriptElement.get().getExpression();
        resultVariableName = scriptElement.get().getResultVariable();
      }
    }

    Map<String, String> headers = new HashMap<>();
    Optional<TaskHeaders> taskDefinitionheaders =
        ExtensionElementHelper.extractExtensionElement(
            scriptTask.getExtensionElements(), TaskHeaders.class);
    taskDefinitionheaders.ifPresent(
        taskHeaders ->
            taskHeaders
                .getHeader()
                .forEach(header -> headers.put(header.getKey(), header.getValue())));

    return new ScriptTaskDTO(
        scriptTask.getId(),
        parentId,
        taskDefinitionId,
        retries,
        mapQNameList(scriptTask.getIncoming()),
        mapQNameList(scriptTask.getOutgoing()),
        loopCharacteristics,
        headers,
        ioMapping,
        scriptType,
        List.of(scriptExpression),
        resultVariableName);
  }
}
