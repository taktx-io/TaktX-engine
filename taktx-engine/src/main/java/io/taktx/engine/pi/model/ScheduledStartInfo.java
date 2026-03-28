/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.TimerEventDefinition;

public record ScheduledStartInfo(
    Scope scope, FlowNode flowNodeToStart, TimerEventDefinition timerEventDefinition) {}
