/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.BaseElementDTO;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.BusinessRuleTaskDTO;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.EndEventDTO;
import io.taktx.dto.ErrorDTO;
import io.taktx.dto.ErrorEventDefinitionDTO;
import io.taktx.dto.EscalationDTO;
import io.taktx.dto.EscalationEventDefinitionDTO;
import io.taktx.dto.EventBasedGatewayDTO;
import io.taktx.dto.ExclusiveGatewayDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.InclusiveGatewayDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IntermediateCatchEventDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.LinkEventDefinitionDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.MessageDTO;
import io.taktx.dto.MessageEndEventDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;
import io.taktx.dto.ParallelGatewayDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.ReceiveTaskDTO;
import io.taktx.dto.ScriptTaskDTO;
import io.taktx.dto.ScriptType;
import io.taktx.dto.SendTaskDTO;
import io.taktx.dto.SequenceFlowDTO;
import io.taktx.dto.ServiceTaskDTO;
import io.taktx.dto.SigDTO;
import io.taktx.dto.SignalEventDefinitionDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TaskDTO;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.TerminateEventDefinitionDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.dto.UserTaskDTO;
import io.taktx.dto.UserTaskTypeEnum;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.proto.BaseElementEnvelope;
import io.taktx.proto.DefinitionsTriggerEnvelope;
import io.taktx.proto.ParsedDefinitionsMessage;
import io.taktx.proto.ProcessDefinitionMessage;
import io.taktx.xml.BpmnParser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefinitionsProtoMapperTest {

  @Test
  void parsedDefinitions_roundTripsWithoutDataLoss() throws Exception {
    ParsedDefinitionsDTO definitions = sampleParsedDefinitions();

    ParsedDefinitionsMessage message = DefinitionsProtoMapper.toProto(definitions);
    ParsedDefinitionsDTO restored =
        DefinitionsProtoMapper.toDto(ParsedDefinitionsMessage.parseFrom(message.toByteArray()));

    assertThat(restored).usingRecursiveComparison().isEqualTo(definitions);
  }

  @ParameterizedTest(name = "{0} round-trips through DefinitionsTriggerEnvelope")
  @MethodSource("triggerCases")
  void definitionsTriggerFamily_roundTrips(String name, DefinitionsTriggerDTO trigger)
      throws Exception {
    DefinitionsTriggerEnvelope envelope = DefinitionsProtoMapper.toProto(trigger);

    DefinitionsTriggerDTO restored =
        DefinitionsProtoMapper.toDto(DefinitionsTriggerEnvelope.parseFrom(envelope.toByteArray()));

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(trigger);
  }

  @Test
  void processDefinition_roundTripsWithoutDataLoss() throws Exception {
    ProcessDefinitionDTO processDefinition =
        new ProcessDefinitionDTO(sampleParsedDefinitions(), 4, ProcessDefinitionStateEnum.INACTIVE);

    ProcessDefinitionDTO restored =
        DefinitionsProtoMapper.toDto(
            ProcessDefinitionMessage.parseFrom(
                DefinitionsProtoMapper.toProto(processDefinition).toByteArray()));

    assertThat(restored).usingRecursiveComparison().isEqualTo(processDefinition);
  }

  @ParameterizedTest(name = "{0} field coverage round-trips through BaseElementEnvelope")
  @MethodSource("selectedElementCases")
  void selectedElements_roundTripWithAllFields(String name, BaseElementDTO element)
      throws Exception {
    BaseElementDTO restored =
        DefinitionsProtoMapper.toDto(
            BaseElementEnvelope.parseFrom(DefinitionsProtoMapper.toProto(element).toByteArray()));

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(element);
  }

  @Test
  void parsedScriptTasks_roundTripWithoutLosingScriptTypeOrOutgoingFlows() throws Exception {
    ParsedDefinitionsDTO parsed = BpmnParser.parse(scriptTasksBpmn());

    ProcessDefinitionDTO restored =
        DefinitionsProtoMapper.toDto(
            ProcessDefinitionMessage.parseFrom(
                DefinitionsProtoMapper.toProto(
                        new ProcessDefinitionDTO(parsed, 1, ProcessDefinitionStateEnum.ACTIVE))
                    .toByteArray()));

    ScriptTaskDTO feelTask =
        (ScriptTaskDTO)
            restored.getDefinitions().getRootProcess().getFlowElements().get("FeelScriptTask_1");
    ScriptTaskDTO jobWorkerTask =
        (ScriptTaskDTO)
            restored
                .getDefinitions()
                .getRootProcess()
                .getFlowElements()
                .get("JobWorkerScriptTask_1");

    assertThat(feelTask.getScriptType()).isEqualTo(ScriptType.FEEL);
    assertThat(feelTask.getScriptExpressions()).containsExactly("=123");
    assertThat(feelTask.getResultVariableName()).isEqualTo("feelResult");
    assertThat(feelTask.getOutgoing()).containsExactly("Flow_1h5qehj");

    assertThat(jobWorkerTask.getScriptType()).isEqualTo(ScriptType.JOBWORKER);
    assertThat(jobWorkerTask.getWorkerDefinition()).isEqualTo("script-jobworker");
    assertThat(jobWorkerTask.getOutgoing()).containsExactly("Flow_17893rn");
  }

  @Test
  void parsedLinkEvents_roundTripWithoutLosingLinkNamesOrOutgoingFlows() throws Exception {
    ParsedDefinitionsDTO parsed = BpmnParser.parse(linkEventsBpmn());

    ProcessDefinitionDTO restored =
        DefinitionsProtoMapper.toDto(
            ProcessDefinitionMessage.parseFrom(
                DefinitionsProtoMapper.toProto(
                        new ProcessDefinitionDTO(parsed, 1, ProcessDefinitionStateEnum.ACTIVE))
                    .toByteArray()));

    IntermediateThrowEventDTO throwEvent =
        (IntermediateThrowEventDTO)
            restored.getDefinitions().getRootProcess().getFlowElements().get("Throw_1");
    IntermediateCatchEventDTO catch1 =
        (IntermediateCatchEventDTO)
            restored.getDefinitions().getRootProcess().getFlowElements().get("Catch_1");
    IntermediateCatchEventDTO catch2 =
        (IntermediateCatchEventDTO)
            restored.getDefinitions().getRootProcess().getFlowElements().get("Catch_2");

    assertThat(throwEvent.getOutgoing()).isEmpty();
    assertThat(throwEvent.getEventDefinitions())
        .singleElement()
        .isInstanceOf(LinkEventDefinitionDTO.class)
        .extracting(link -> ((LinkEventDefinitionDTO) link).getName())
        .isEqualTo("LinkName");

    assertThat(catch1.getOutgoing()).containsExactly("Flow_0xo3h0u");
    assertThat(catch1.getEventDefinitions())
        .singleElement()
        .isInstanceOf(LinkEventDefinitionDTO.class)
        .extracting(link -> ((LinkEventDefinitionDTO) link).getName())
        .isEqualTo("LinkName");

    assertThat(catch2.getOutgoing()).containsExactly("Flow_0fqyx3j");
    assertThat(catch2.getEventDefinitions())
        .singleElement()
        .isInstanceOf(LinkEventDefinitionDTO.class)
        .extracting(link -> ((LinkEventDefinitionDTO) link).getName())
        .isEqualTo("LinkName2");
  }

  private static Stream<Arguments> triggerCases() {
    return Stream.of(
        Arguments.of("xmlDefinitions", new XmlDefinitionsDTO("<bpmn id=\"orders\"/>")),
        Arguments.of("parsedDefinitions", sampleParsedDefinitions()),
        Arguments.of(
            "processDefinitionActivation",
            new ProcessDefinitionActivationDTO(
                new ProcessDefinitionKey("orders", 5), ProcessDefinitionStateEnum.INACTIVE)));
  }

  private static Stream<Arguments> selectedElementCases() {
    return Stream.of(
        Arguments.of("serviceTask", sampleServiceTask()),
        Arguments.of("userTask", sampleUserTask()),
        Arguments.of("subProcess", sampleSubProcess()),
        Arguments.of("callActivity", sampleCallActivity()));
  }

  static ParsedDefinitionsDTO sampleParsedDefinitions() {
    Map<String, FlowElementDTO> elements = new LinkedHashMap<>();

    StartEventDTO startEvent =
        new StartEventDTO(
            "start",
            "process-root",
            "Start",
            linkedSet(),
            linkedSet("flow-1"),
            linkedSet(
                new MessageEventDefinitionDTO("evt-msg-start", "message-order-created"),
                new SignalEventDefinitionDTO("evt-sig-start", "signal-order-start")),
            ioMapping("=input.customerId", "customerId", "=input.priority", "priority"),
            true);
    elements.put(startEvent.getId(), startEvent);

    BoundaryEventDTO boundaryEvent =
        new BoundaryEventDTO(
            "boundary",
            "process-root",
            "Boundary",
            linkedSet("flow-7"),
            linkedSet("flow-8"),
            linkedSet(
                new TimerEventDefinitionDTO(
                    "evt-timer-boundary", "boundary", "2026-05-18T08:00:00Z", "PT15M", "R3/PT5M"),
                new ErrorEventDefinitionDTO("evt-error-boundary", "error-payment")),
            "service-task",
            true,
            ioMapping("=boundary.input", "boundaryInput", "=boundary.output", "boundaryOutput"));
    elements.put(boundaryEvent.getId(), boundaryEvent);

    IntermediateCatchEventDTO catchEvent =
        new IntermediateCatchEventDTO(
            "catch",
            "process-root",
            "Catch",
            linkedSet("flow-8"),
            linkedSet("flow-9"),
            linkedSet(new LinkEventDefinitionDTO("evt-link-catch", "resume-link")),
            ioMapping("=catch.in", "catchIn", "=catch.out", "catchOut"));
    elements.put(catchEvent.getId(), catchEvent);

    IntermediateThrowEventDTO throwEvent =
        new IntermediateThrowEventDTO(
            "throw",
            "process-root",
            "Throw",
            linkedSet("flow-9"),
            linkedSet("flow-10"),
            ioMapping("=throw.in", "throwIn", "=throw.out", "throwOut"),
            linkedSet(new EscalationEventDefinitionDTO("evt-esc-throw", "esc-review")));
    elements.put(throwEvent.getId(), throwEvent);

    EndEventDTO endEvent =
        new EndEventDTO(
            "end",
            "process-root",
            "End",
            linkedSet("flow-20"),
            linkedSet(),
            ioMapping("=end.in", "endIn", "=end.out", "endOut"),
            linkedSet(new TerminateEventDefinitionDTO("evt-term-end")));
    elements.put(endEvent.getId(), endEvent);

    elements.put(
        "inclusive-gw",
        new InclusiveGatewayDTO(
            "inclusive-gw",
            "process-root",
            "Inclusive",
            linkedSet("flow-10"),
            linkedSet("flow-11", "flow-12"),
            "flow-11"));
    elements.put(
        "event-based-gw",
        new EventBasedGatewayDTO(
            "event-based-gw",
            "process-root",
            "EventBased",
            linkedSet("flow-12"),
            linkedSet("flow-13", "flow-14"),
            "flow-13"));
    elements.put(
        "parallel-gw",
        new ParallelGatewayDTO(
            "parallel-gw", "process-root", "Parallel", linkedSet("flow-14"), linkedSet("flow-15")));
    elements.put(
        "exclusive-gw",
        new ExclusiveGatewayDTO(
            "exclusive-gw",
            "process-root",
            "Exclusive",
            linkedSet("flow-15"),
            linkedSet("flow-16", "flow-17"),
            "flow-17"));

    elements.put(sampleSubProcess().getId(), sampleSubProcess());
    elements.put(sampleCallActivity().getId(), sampleCallActivity());

    elements.put(
        "receive-task",
        new ReceiveTaskDTO(
            "receive-task",
            "process-root",
            "Receive",
            linkedSet("flow-16"),
            linkedSet("flow-18"),
            loopCharacteristics(false, "=orders", "order", "=received", "receivedOrder"),
            "message-payment-received",
            ioMapping("=receive.in", "receiveIn", "=receive.out", "receiveOut")));

    elements.put(
        "send-task",
        new SendTaskDTO(
            "send-task",
            "process-root",
            "Send",
            "notify-worker",
            "5",
            linkedSet("flow-17"),
            linkedSet("flow-19"),
            "notifyImplementation",
            loopCharacteristics(
                true, "=notifications", "notification", "=sent", "sentNotification"),
            linkedMap("type", "email", "priority", "high"),
            ioMapping("=send.in", "sendIn", "=send.out", "sendOut")));

    elements.put(sampleServiceTask().getId(), sampleServiceTask());

    elements.put(
        "message-end",
        new MessageEndEventDTO(
            "message-end",
            "process-root",
            "MessageEnd",
            "notify-end-worker",
            "2",
            linkedSet("flow-18"),
            linkedSet("flow-20"),
            linkedMap("channel", "audit"),
            ioMapping("=msg.end.in", "msgEndIn", "=msg.end.out", "msgEndOut")));

    elements.put(
        "message-throw",
        new MessageIntermediateThrowEventDTO(
            "message-throw",
            "process-root",
            "MessageThrow",
            "notify-throw-worker",
            "3",
            linkedSet("flow-19"),
            linkedSet("flow-20"),
            linkedMap("channel", "ops"),
            ioMapping("=msg.throw.in", "msgThrowIn", "=msg.throw.out", "msgThrowOut")));

    elements.put(
        "business-rule",
        new BusinessRuleTaskDTO(
            "business-rule",
            "process-root",
            "BusinessRule",
            linkedSet("flow-11"),
            linkedSet("flow-21"),
            loopCharacteristics(false, "=rules", "rule", "=results", "result"),
            ioMapping("=br.in", "brIn", "=br.out", "brOut"),
            "eligibility-dmn",
            "decisionResult"));

    elements.put(
        "script-task",
        new ScriptTaskDTO(
            "script-task",
            "process-root",
            "Script",
            "script-worker",
            "4",
            linkedSet("flow-13"),
            linkedSet("flow-22"),
            loopCharacteristics(false, "=items", "item", "=scriptResult", "scriptEntry"),
            linkedMap("lang", "python"),
            ioMapping("=script.in", "scriptIn", "=script.out", "scriptOut"),
            ScriptType.PYTHON,
            List.of("=items", "=total + 1"),
            "scriptResult"));

    elements.put(sampleUserTask().getId(), sampleUserTask());

    elements.put(
        "task",
        new TaskDTO(
            "task",
            "process-root",
            "Task",
            linkedSet("flow-22"),
            linkedSet("flow-23"),
            loopCharacteristics(false, "=plainItems", "plainItem", "=plainOut", "plainResult"),
            ioMapping("=task.in", "taskIn", "=task.out", "taskOut")));

    elements.put(
        "sequence-1",
        new SequenceFlowDTO(
            "sequence-1",
            "process-root",
            "Sequence",
            "start",
            "service-task",
            new FlowConditionDTO("= approved")));

    ProcessDTO rootProcess =
        new ProcessDTO("process-root", null, "v2026.05", new FlowElementsDTO(elements));

    return new ParsedDefinitionsDTO(
        new DefinitionsKey("orders", "hash-proto-45"),
        rootProcess,
        linkedMap(
            "message-order-created",
                new MessageDTO("message-order-created", "order.created", "=orderId"),
            "message-payment-received",
                new MessageDTO("message-payment-received", "payment.received", "=paymentId")),
        linkedMap("esc-review", new EscalationDTO("esc-review", "ReviewEscalation", "REVIEW")),
        linkedMap("error-payment", new ErrorDTO("error-payment", "PaymentError", "PAY-001")),
        linkedMap("signal-order-start", new SigDTO("signal-order-start", "order-started")));
  }

  private static ServiceTaskDTO sampleServiceTask() {
    return new ServiceTaskDTO(
        "service-task",
        "process-root",
        "Service",
        "service-worker",
        "7",
        linkedSet("flow-1"),
        linkedSet("flow-7"),
        "serviceImplementation",
        loopCharacteristics(true, "=lines", "line", "=serviceOut", "serviceItem"),
        linkedMap("header-a", "value-a", "header-b", "value-b"),
        ioMapping("=service.in", "serviceIn", "=service.out", "serviceOut"));
  }

  private static UserTaskDTO sampleUserTask() {
    return new UserTaskDTO(
        "user-task",
        "process-root",
        "User",
        linkedSet("flow-21"),
        linkedSet("flow-24"),
        loopCharacteristics(false, "=assignees", "assignee", "=userOut", "userResult"),
        ioMapping("=user.in", "userIn", "=user.out", "userOut"),
        linkedMap("formKey", "order-approval"),
        UserTaskTypeEnum.JOBWORKER,
        new AssignmentDefinitionDTO("alice", "sales,finance", "alice,bob"),
        new TaskScheduleDTO("2026-05-19T09:00:00Z", "2026-05-20T12:00:00Z"),
        new PriorityDefinitionDTO("=priority"));
  }

  private static SubProcessDTO sampleSubProcess() {
    Map<String, FlowElementDTO> innerElements = new LinkedHashMap<>();
    innerElements.put(
        "sub-task",
        new TaskDTO(
            "sub-task",
            "sub-process",
            "SubTask",
            linkedSet("sub-flow-1"),
            linkedSet("sub-flow-2"),
            loopCharacteristics(false, "=subItems", "subItem", "=subOut", "subResult"),
            ioMapping("=sub.in", "subIn", "=sub.out", "subOut")));
    innerElements.put(
        "sub-sequence",
        new SequenceFlowDTO(
            "sub-sequence",
            "sub-process",
            "SubSequence",
            "sub-task",
            "sub-task",
            FlowConditionDTO.NONE));

    return new SubProcessDTO(
        "sub-process",
        "process-root",
        "SubProcess",
        linkedSet("flow-23"),
        linkedSet("flow-25"),
        loopCharacteristics(false, "=subCollection", "subElement", "=subOutputs", "subOutput"),
        new FlowElementsDTO(innerElements),
        ioMapping("=subprocess.in", "subProcessIn", "=subprocess.out", "subProcessOut"),
        true);
  }

  private static CallActivityDTO sampleCallActivity() {
    return new CallActivityDTO(
        "call-activity",
        "process-root",
        "CallActivity",
        linkedSet("flow-24"),
        linkedSet("flow-26"),
        loopCharacteristics(true, "=calledInputs", "calledInput", "=calledOutputs", "calledOutput"),
        "child-process",
        true,
        false,
        ioMapping("=call.in", "callIn", "=call.out", "callOut"));
  }

  @SafeVarargs
  private static <T> LinkedHashSet<T> linkedSet(T... values) {
    LinkedHashSet<T> set = new LinkedHashSet<>();
    if (values != null) {
      set.addAll(List.of(values));
    }
    return set;
  }

  private static <T> LinkedHashMap<String, T> linkedMap(String key1, T value1) {
    LinkedHashMap<String, T> map = new LinkedHashMap<>();
    map.put(key1, value1);
    return map;
  }

  private static <T> LinkedHashMap<String, T> linkedMap(
      String key1, T value1, String key2, T value2) {
    LinkedHashMap<String, T> map = new LinkedHashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  private static InputOutputMappingDTO ioMapping(
      String inputSource1, String inputTarget1, String outputSource1, String outputTarget1) {
    return new InputOutputMappingDTO(
        linkedSet(new IoVariableMappingDTO(inputSource1, inputTarget1)),
        linkedSet(new IoVariableMappingDTO(outputSource1, outputTarget1)));
  }

  private static LoopCharacteristicsDTO loopCharacteristics(
      boolean sequential,
      String inputCollection,
      String inputElement,
      String outputCollection,
      String outputElement) {
    return new LoopCharacteristicsDTO(
        sequential, inputCollection, inputElement, outputCollection, outputElement);
  }

  private static String scriptTasksBpmn() {
    return """
        <?xml version=\"1.0\" encoding=\"UTF-8\"?>
        <bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\" id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">
          <bpmn:process id=\"Process_1\" isExecutable=\"true\">
            <bpmn:startEvent id=\"StartEvent_1\">
              <bpmn:outgoing>Flow_11a7l5f</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:sequenceFlow id=\"Flow_11a7l5f\" sourceRef=\"StartEvent_1\" targetRef=\"FeelScriptTask_1\" />
            <bpmn:scriptTask id=\"FeelScriptTask_1\" name=\"Feel\">
              <bpmn:extensionElements>
                <zeebe:script expression=\"=123\" resultVariable=\"feelResult\" />
              </bpmn:extensionElements>
              <bpmn:incoming>Flow_11a7l5f</bpmn:incoming>
              <bpmn:outgoing>Flow_1h5qehj</bpmn:outgoing>
            </bpmn:scriptTask>
            <bpmn:sequenceFlow id=\"Flow_1h5qehj\" sourceRef=\"FeelScriptTask_1\" targetRef=\"JobWorkerScriptTask_1\" />
            <bpmn:scriptTask id=\"JobWorkerScriptTask_1\" name=\"JobWorker\">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type=\"script-jobworker\" />
              </bpmn:extensionElements>
              <bpmn:incoming>Flow_1h5qehj</bpmn:incoming>
              <bpmn:outgoing>Flow_17893rn</bpmn:outgoing>
            </bpmn:scriptTask>
            <bpmn:sequenceFlow id=\"Flow_17893rn\" sourceRef=\"JobWorkerScriptTask_1\" targetRef=\"EndEvent_1\" />
            <bpmn:endEvent id=\"EndEvent_1\">
              <bpmn:incoming>Flow_17893rn</bpmn:incoming>
            </bpmn:endEvent>
          </bpmn:process>
        </bpmn:definitions>
        """;
  }

  private static String linkEventsBpmn() {
    return """
        <?xml version=\"1.0\" encoding=\"UTF-8\"?>
        <bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\" id=\"Definitions_2\" targetNamespace=\"http://bpmn.io/schema/bpmn\">
          <bpmn:process id=\"SolutionFulfillmentProcess\" isExecutable=\"true\">
            <bpmn:startEvent id=\"StartEvent_1\">
              <bpmn:outgoing>Flow_17qbg2m</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:sequenceFlow id=\"Flow_17qbg2m\" sourceRef=\"StartEvent_1\" targetRef=\"Throw_1\" />
            <bpmn:intermediateThrowEvent id=\"Throw_1\">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:output source=\"=123\" target=\"linkOutput_1\" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
              <bpmn:incoming>Flow_17qbg2m</bpmn:incoming>
              <bpmn:linkEventDefinition id=\"LinkEventDefinition_1\" name=\"LinkName\" />
            </bpmn:intermediateThrowEvent>
            <bpmn:intermediateCatchEvent id=\"Catch_1\">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:output source=\"=456\" target=\"linkOutput_2\" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
              <bpmn:outgoing>Flow_0xo3h0u</bpmn:outgoing>
              <bpmn:linkEventDefinition id=\"LinkEventDefinition_2\" name=\"LinkName\" />
            </bpmn:intermediateCatchEvent>
            <bpmn:sequenceFlow id=\"Flow_0xo3h0u\" sourceRef=\"Catch_1\" targetRef=\"EndEvent_1\" />
            <bpmn:intermediateCatchEvent id=\"Catch_2\">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:output source=\"=789\" target=\"linkOutput_2\" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
              <bpmn:outgoing>Flow_0fqyx3j</bpmn:outgoing>
              <bpmn:linkEventDefinition id=\"LinkEventDefinition_3\" name=\"LinkName2\" />
            </bpmn:intermediateCatchEvent>
            <bpmn:sequenceFlow id=\"Flow_0fqyx3j\" sourceRef=\"Catch_2\" targetRef=\"EndEvent_1\" />
            <bpmn:endEvent id=\"EndEvent_1\">
              <bpmn:incoming>Flow_0xo3h0u</bpmn:incoming>
              <bpmn:incoming>Flow_0fqyx3j</bpmn:incoming>
            </bpmn:endEvent>
          </bpmn:process>
        </bpmn:definitions>
        """;
  }
}
