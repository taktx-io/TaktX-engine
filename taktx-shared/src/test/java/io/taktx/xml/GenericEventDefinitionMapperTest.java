/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.bpmn.TErrorEventDefinition;
import io.taktx.bpmn.TEscalationEventDefinition;
import io.taktx.bpmn.TEventDefinition;
import io.taktx.bpmn.TExpression;
import io.taktx.bpmn.TLinkEventDefinition;
import io.taktx.bpmn.TMessageEventDefinition;
import io.taktx.bpmn.TSignalEventDefinition;
import io.taktx.bpmn.TTerminateEventDefinition;
import io.taktx.bpmn.TTimerEventDefinition;
import io.taktx.dto.ErrorEventDefinitionDTO;
import io.taktx.dto.EscalationEventDefinitionDTO;
import io.taktx.dto.EventDefinitionDTO;
import io.taktx.dto.LinkEventDefinitionDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.SignalEventDefinitionDTO;
import io.taktx.dto.TerminateEventDefinitionDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

class GenericEventDefinitionMapperTest {

  private final GenericEventDefinitionMapper mapper = new GenericEventDefinitionMapper();

  @Test
  void map_supportsAllKnownEventDefinitionTypes() {
    TTimerEventDefinition timer = new TTimerEventDefinition();
    timer.setId("timer-1");
    timer.setTimeDate(expression("2026-05-26T12:00:00Z"));

    TMessageEventDefinition message = new TMessageEventDefinition();
    message.setId("message-1");
    message.setMessageRef(new QName("urn:test", "order-message"));

    TLinkEventDefinition link = new TLinkEventDefinition();
    link.setId("link-1");
    link.setName("link-target");

    TTerminateEventDefinition terminate = new TTerminateEventDefinition();
    terminate.setId("terminate-1");

    TEscalationEventDefinition escalation = new TEscalationEventDefinition();
    escalation.setId("escalation-1");
    escalation.setEscalationRef(new QName("urn:test", "escalation-ref"));

    TErrorEventDefinition error = new TErrorEventDefinition();
    error.setId("error-1");
    error.setErrorRef(new QName("urn:test", "error-ref"));

    TSignalEventDefinition signal = new TSignalEventDefinition();
    signal.setId("signal-1");
    signal.setSignalRef(new QName("urn:test", "signal-ref"));

    Set<EventDefinitionDTO> result =
        mapper.map(
            List.of(
                event(timer, TTimerEventDefinition.class, "timerEventDefinition"),
                event(message, TMessageEventDefinition.class, "messageEventDefinition"),
                event(link, TLinkEventDefinition.class, "linkEventDefinition"),
                event(terminate, TTerminateEventDefinition.class, "terminateEventDefinition"),
                event(escalation, TEscalationEventDefinition.class, "escalationEventDefinition"),
                event(error, TErrorEventDefinition.class, "errorEventDefinition"),
                event(signal, TSignalEventDefinition.class, "signalEventDefinition")),
            "parent-process");

    assertThat(result)
        .hasSize(7)
        .anyMatch(TimerEventDefinitionDTO.class::isInstance)
        .anyMatch(MessageEventDefinitionDTO.class::isInstance)
        .anyMatch(LinkEventDefinitionDTO.class::isInstance)
        .anyMatch(TerminateEventDefinitionDTO.class::isInstance)
        .anyMatch(EscalationEventDefinitionDTO.class::isInstance)
        .anyMatch(ErrorEventDefinitionDTO.class::isInstance)
        .anyMatch(SignalEventDefinitionDTO.class::isInstance);
    assertThat(result)
        .filteredOn(TimerEventDefinitionDTO.class::isInstance)
        .singleElement()
        .extracting(dto -> ((TimerEventDefinitionDTO) dto).getTimeDate())
        .isEqualTo("2026-05-26T12:00:00Z");
    assertThat(result)
        .filteredOn(MessageEventDefinitionDTO.class::isInstance)
        .singleElement()
        .extracting(dto -> ((MessageEventDefinitionDTO) dto).getMessageRef())
        .isEqualTo("order-message");
  }

  @Test
  void map_timerDefinitionPreservesDurationAndCycleContent() {
    TTimerEventDefinition timer = new TTimerEventDefinition();
    timer.setId("timer-2");
    timer.setTimeDuration(expression("PT5M"));
    timer.setTimeCycle(expression("R3/PT1M"));

    Set<EventDefinitionDTO> result =
        mapper.map(
            List.of(event(timer, TTimerEventDefinition.class, "timerEventDefinition")), "flow-1");

    assertThat(result)
        .filteredOn(TimerEventDefinitionDTO.class::isInstance)
        .singleElement()
        .satisfies(
            dto -> {
              TimerEventDefinitionDTO timerDto = (TimerEventDefinitionDTO) dto;
              assertThat(timerDto.getParentId()).isEqualTo("flow-1");
              assertThat(timerDto.getTimeDuration()).isEqualTo("PT5M");
              assertThat(timerDto.getTimeCycle()).isEqualTo("R3/PT1M");
            });
  }

  @Test
  void map_unknownEventDefinition_throwsIllegalStateException() {
    TEventDefinition unknown = new TEventDefinition() {};
    unknown.setId("unknown-1");
    JAXBElement<TEventDefinition> wrapped =
        event(unknown, TEventDefinition.class, "eventDefinition");

    assertThatThrownBy(() -> mapper.map(List.of(wrapped), "parent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown event definition");
  }

  private static TExpression expression(String value) {
    TExpression expression = new TExpression();
    expression.getContent().add(value);
    return expression;
  }

  private static <T extends TEventDefinition> JAXBElement<T> event(
      T value, Class<T> type, String localPart) {
    return new JAXBElement<>(new QName("urn:test", localPart), type, value);
  }
}
