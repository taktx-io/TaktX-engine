#!/usr/bin/env python3
"""
generate_protos.py
==================
Parses every Java DTO in taktx-shared and generates the matching .proto files
under taktx-shared/src/main/proto/io/taktx/proto/.

Usage:
    python3 scripts/generate_protos.py [--dry-run]

The script does NOT need javac / protoc to run; it's pure regex-based parsing.

Generated files are STUBS — the oneof/polymorphic wiring is post-processed via
the ONEOF_OVERRIDES table below. Review the output and commit after confirming
field numbers are final.
"""

import re
import os
import sys
import textwrap
from pathlib import Path
from collections import defaultdict

# ─────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────
REPO_ROOT = Path(__file__).resolve().parent.parent
DTO_DIR   = REPO_ROOT / "taktx-shared/src/main/java/io/taktx/dto"
SUBPKG    = REPO_ROOT / "taktx-shared/src/main/java/io/taktx/dto/subscriptions"
OUT_DIR   = REPO_ROOT / "taktx-shared/src/main/proto/io/taktx/proto"

PROTO_PACKAGE    = "io.taktx.proto"
JAVA_PACKAGE     = "io.taktx.proto"
PROTO_HEADER = """\
syntax = "proto3";

package {pkg};

option java_package = "{jpkg}";
option java_multiple_files = true;
option java_outer_classname = "{outer}";
"""

# ─────────────────────────────────────────────
# JAVA → PROTO type mapping
# ─────────────────────────────────────────────
# Keyed on the raw Java type token (simple name / qualified fragment).
TYPE_MAP = {
    # primitives
    "boolean": "bool",
    "Boolean": "bool",
    "int":     "int32",
    "Integer": "int32",
    "short":   "int32",
    "Short":   "int32",
    "long":    "sint64",
    "Long":    "sint64",
    "double":  "double",
    "Double":  "double",
    "float":   "float",
    "Float":   "float",
    "byte[]":  "bytes",
    "String":  "string",
    "Instant": "sint64",  # epoch-ms

    # domain types
    "UUID":             "Uuid",           # common.proto
    "VariablesDTO":     "VarMap",         # variables.proto
    "JsonNode":         "VariableValue",  # variables.proto

    # nested DTOs → proto message names (snake_case class → PascalCase message)
    # (most are resolved dynamically — these are overrides for renamed ones)
    "ScopeDTO":                     "ScopeMessage",
    "SubscriptionsDTO":             "SubscriptionsMessage",
    "ProcessDefinitionKey":         "ProcessDefinitionKeyMessage",
    "DefinitionsKey":               "DefinitionsKeyMessage",
    "DmnDefinitionsKey":            "DmnDefinitionsKeyMessage",
    "IncidentInfoDTO":              "IncidentInfoMessage",
    "IoVariableMappingDTO":         "IoVariableMappingMessage",
    "InputOutputMappingDTO":        "InputOutputMappingMessage",
    "LoopCharacteristicsDTO":       "LoopCharacteristicsMessage",
    "FlowElementsDTO":              "FlowElementsMessage",
    "AssignmentDefinitionDTO":      "AssignmentDefinitionMessage",
    "TaskScheduleDTO":              "TaskScheduleMessage",
    "PriorityDefinitionDTO":        "PriorityDefinitionMessage",
    "FlowConditionDTO":             "FlowConditionMessage",
    "CommandTrustMetadataDTO":      "CommandTrustMetadataMessage",
    "ScheduleKeyDTO":               "ScheduleKeyMessage",
    "InstanceScheduleKeyDTO":       "ScheduleKeyMessage",   # flattened in schedule.proto
    "DefinitionScheduleKeyDTO":     "ScheduleKeyMessage",   # flattened in schedule.proto
    "MessageScheduleDTO":           "MessageScheduleMessage",
    "EventSignalDTO":               "EventSignalMessage",
    "MessageEventDTO":              "MessageEventMessage",
    "SignalDTO":                    "SignalMessage",
    "SchedulableMessageDTO":        "ProcessInstanceTriggerMessage",  # interface
    "ExternalTaskResponseResultDTO":"ExternalTaskResponseResultMessage",
    "UserTaskResponseResultDTO":    "UserTaskResponseResultMessage",
    "ParsedDefinitionsDTO":         "ParsedDefinitionsMessage",
    "ParsedDmnDefinitionsDTO":      "ParsedDmnDefinitionsMessage",
    "DmnDecisionTableDTO":          "DmnDecisionTableMessage",
    "DmnLiteralExpressionDTO":      "DmnLiteralExpressionMessage",
    "DmnDecisionDTO":               "DmnDecisionMessage",
    "DmnInputClauseDTO":            "DmnInputClauseMessage",
    "DmnOutputClauseDTO":           "DmnOutputClauseMessage",
    "DmnRuleDTO":                   "DmnRuleMessage",
    "MessageDTO":                   "MessageDefMessage",
    "EscalationDTO":                "EscalationDefMessage",
    "ErrorDTO":                     "ErrorDefMessage",
    "SigDTO":                       "SignalDefMessage",
    "DlqLineageDTO":                "DlqLineageMessage",
    "TimeBucket":                   "TimeBucket",  # enum
}

# Enum class names → keep as proto enum
ENUM_NAMES = {
    "ExecutionState", "DmnValidationMode", "ReplayProtectionMode",
    "ProcessDefinitionStateEnum", "DmnDefinitionStateEnum",
    "CleanupPolicy", "DlqSeverity", "DlqCaptureStage", "DlqReasonCode",
    "ExternalTaskResponseType", "UserTaskResponseType",
    "CommandAuthMethod", "CommandTrustVerificationResult",
    "KeyRole", "ReplayValidationPolicy", "ScriptType",
    "UserTaskTypeEnum", "TimeBucket",
    "DmnHitPolicy", "DmnCollectOperator",
}

# ─────────────────────────────────────────────
# FILE ASSIGNMENTS
# Determines which .proto file each DTO class ends up in.
# Classes not listed here are put into "misc.proto".
# ─────────────────────────────────────────────
FILE_ASSIGNMENT = {
    # variables.proto
    "VariablesDTO": "variables",  # becomes VarMap

    # common.proto
    "ProcessDefinitionKey":     "common",
    "DefinitionsKey":           "common",
    "DmnDefinitionsKey":        "common",
    "IncidentInfoDTO":          "common",
    "IoVariableMappingDTO":     "common",
    "InputOutputMappingDTO":    "common",
    "ExecutionState":           "common",
    "FlowConditionDTO":         "common",

    # process_instance.proto
    "ProcessInstanceDTO":    "process_instance",
    "ScopeDTO":              "process_instance",
    "SubscriptionsDTO":      "process_instance",
    "SubscriptionDTO":       "process_instance",
    # subscriptions/** handled below

    # flow_node_instance.proto
    "FlowNodeInstanceDTO":           "flow_node_instance",
    "ActivityInstanceDTO":           "flow_node_instance",
    "CatchEventInstanceDTO":         "flow_node_instance",
    "EventInstanceDTO":              "flow_node_instance",
    "ExternalTaskInstanceDTO":       "flow_node_instance",
    "GatewayInstanceDTO":            "flow_node_instance",
    "TaskInstanceDTO":               "flow_node_instance",
    "StartEventInstanceDTO":         "flow_node_instance",
    "BoundaryEventInstanceDTO":      "flow_node_instance",
    "CallActivityInstanceDTO":       "flow_node_instance",
    "SendTaskInstanceDTO":           "flow_node_instance",
    "EndEventInstanceDTO":           "flow_node_instance",
    "ScriptTaskInstanceDTO":         "flow_node_instance",
    "MessageEndEventInstanceDTO":    "flow_node_instance",
    "MessageIntermediateThrowEventInstanceDTO": "flow_node_instance",
    "IntermediateCatchEventInstanceDTO":        "flow_node_instance",
    "EventBasedGatewayInstanceDTO":  "flow_node_instance",
    "BusinessRuleTaskInstanceDTO":   "flow_node_instance",
    "MultiInstanceInstanceDTO":      "flow_node_instance",
    "InclusiveGatewayInstanceDTO":   "flow_node_instance",
    "ParallelGatewayInstanceDTO":    "flow_node_instance",
    "ReceiveTaskInstanceDTO":        "flow_node_instance",
    "SubProcessInstanceDTO":         "flow_node_instance",
    "TaskInstanceDTOConcrete":       "flow_node_instance",
    "UserTaskInstanceDTO":           "flow_node_instance",
    "ServiceTaskInstanceDTO":        "flow_node_instance",
    "IntermediateThrowEventInstanceDTO": "flow_node_instance",
    "ExclusiveGatewayInstanceDTO":   "flow_node_instance",

    # instance_update.proto
    "InstanceUpdateDTO":          "instance_update",
    "FlowNodeInstanceUpdateDTO":  "instance_update",
    "ProcessInstanceUpdateDTO":   "instance_update",
    "CommandTrustMetadataDTO":    "instance_update",
    "CommandAuthMethod":          "instance_update",
    "CommandTrustVerificationResult": "instance_update",

    # process_instance_trigger.proto
    "ProcessInstanceTriggerDTO":        "process_instance_trigger",
    "StartCommandDTO":                  "process_instance_trigger",
    "ContinueFlowElementTriggerDTO":    "process_instance_trigger",
    "ExternalTaskTriggerDTO":           "process_instance_trigger",
    "SetVariableTriggerDTO":            "process_instance_trigger",
    "ExternalTaskResponseTriggerDTO":   "process_instance_trigger",
    "StartFlowElementTriggerDTO":       "process_instance_trigger",
    "AbortTriggerDTO":                  "process_instance_trigger",
    "UserTaskResponseTriggerDTO":       "process_instance_trigger",
    "EventSignalTriggerDTO":            "process_instance_trigger",
    "ExternalTaskResponseResultDTO":    "process_instance_trigger",
    "UserTaskResponseResultDTO":        "process_instance_trigger",
    "ExternalTaskResponseType":         "process_instance_trigger",
    "UserTaskResponseType":             "process_instance_trigger",
    "EventSignalDTO":                   "process_instance_trigger",
    "MessageEventSignalDTO":            "process_instance_trigger",
    "ErrorEventSignalDTO":              "process_instance_trigger",
    "EscalationEventSignalDTO":         "process_instance_trigger",
    "TimerEventSignalDTO":              "process_instance_trigger",
    "SignalEventSignalDTO":             "process_instance_trigger",

    # definitions.proto
    "DefinitionsTriggerDTO":            "definitions",
    "ParsedDefinitionsDTO":             "definitions",
    "XmlDefinitionsDTO":                "definitions",
    "ProcessDefinitionActivationDTO":   "definitions",
    "ProcessDefinitionStateEnum":       "definitions",
    "BaseElementDTO":                   "definitions",
    "FlowElementDTO":                   "definitions",
    "FlowNodeDTO":                      "definitions",
    "RootElementDTO":                   "definitions",
    "ActivityDTO":                      "definitions",
    "EventDTO":                         "definitions",
    "CatchEventDTO":                    "definitions",
    "ThrowEventDTO":                    "definitions",
    "ProcessDTO":                       "definitions",
    "FlowElementsDTO":                  "definitions",
    "SequenceFlowDTO":                  "definitions",
    "BoundaryEventDTO":                 "definitions",
    "StartEventDTO":                    "definitions",
    "EndEventDTO":                      "definitions",
    "IntermediateCatchEventDTO":        "definitions",
    "IntermediateThrowEventDTO":        "definitions",
    "MessageEndEventDTO":               "definitions",
    "MessageIntermediateThrowEventDTO": "definitions",
    "SubProcessDTO":                    "definitions",
    "CallActivityDTO":                  "definitions",
    "TaskDTO":                          "definitions",
    "UserTaskDTO":                      "definitions",
    "ServiceTaskDTO":                   "definitions",
    "SendTaskDTO":                      "definitions",
    "ScriptTaskDTO":                    "definitions",
    "ReceiveTaskDTO":                   "definitions",
    "BusinessRuleTaskDTO":              "definitions",
    "ExternalTaskDTO":                  "definitions",
    "InclusiveGatewayDTO":              "definitions",
    "ExclusiveGatewayDTO":              "definitions",
    "ParallelGatewayDTO":               "definitions",
    "EventBasedGatewayDTO":             "definitions",
    "LoopCharacteristicsDTO":           "definitions",
    "InputOutputMappingDTO":            "definitions",
    "AssignmentDefinitionDTO":          "definitions",
    "TaskScheduleDTO":                  "definitions",
    "PriorityDefinitionDTO":            "definitions",
    "EventDefinitionDTO":               "definitions",
    "TimerEventDefinitionDTO":          "definitions",
    "MessageEventDefinitionDTO":        "definitions",
    "SignalEventDefinitionDTO":         "definitions",
    "ErrorEventDefinitionDTO":          "definitions",
    "EscalationEventDefinitionDTO":     "definitions",
    "LinkEventDefinitionDTO":           "definitions",
    "TerminateEventDefinitionDTO":      "definitions",
    "MessageDTO":                       "definitions",
    "EscalationDTO":                    "definitions",
    "ErrorDTO":                         "definitions",
    "SigDTO":                           "definitions",
    "FlowConditionDTO":                 "definitions",
    "ScriptType":                       "definitions",
    "UserTaskTypeEnum":                 "definitions",
    "WithIoMappingDTO":                 "definitions",

    # dmn_definitions.proto
    "DmnDefinitionsTriggerDTO":    "dmn_definitions",
    "XmlDmnDefinitionsDTO":        "dmn_definitions",
    "DmnDefinitionDTO":            "dmn_definitions",
    "ParsedDmnDefinitionsDTO":     "dmn_definitions",
    "DmnDecisionDTO":              "dmn_definitions",
    "DmnDecisionTableDTO":         "dmn_definitions",
    "DmnInputClauseDTO":           "dmn_definitions",
    "DmnOutputClauseDTO":          "dmn_definitions",
    "DmnRuleDTO":                  "dmn_definitions",
    "DmnLiteralExpressionDTO":     "dmn_definitions",
    "DmnHitPolicy":                "dmn_definitions",
    "DmnCollectOperator":          "dmn_definitions",
    "DmnValidationMode":           "dmn_definitions",
    "DmnDefinitionStateEnum":      "dmn_definitions",
    "DmnDefinitionsDlqEntryDTO":   "dmn_definitions",

    # message_event.proto
    "MessageEventDTO":                        "message_event",
    "DefinitionMessageSubscriptionDTO":       "message_event",
    "CancelDefinitionMessageSubscriptionDTO": "message_event",
    "CorrelationMessageSubscriptionDTO":      "message_event",
    "CancelCorrelationMessageSubscriptionDTO":"message_event",
    "DefinitionMessageEventTriggerDTO":       "message_event",
    "CorrelationMessageEventTriggerDTO":      "message_event",
    "MessageEventKeyDTO":                     "message_event",

    # signals.proto
    "SignalDTO":                            "signals",
    "NewInstanceSignalSubscriptionDTO":     "signals",
    "CancelInstanceSignalSubscriptionDTO":  "signals",
    "NewDefinitionSignalSubscriptionDTO":   "signals",
    "CancelDefinitionSignalSubscriptionDTO":"signals",

    # user_task.proto  (UserTaskDTO is in definitions.proto; this is triggers/responses)

    # schedule.proto
    "ScheduleKeyDTO":              "schedule",
    "InstanceScheduleKeyDTO":      "schedule",
    "DefinitionScheduleKeyDTO":    "schedule",
    "MessageScheduleDTO":          "schedule",
    "FixedRateMessageScheduleDTO": "schedule",
    "OneTimeScheduleDTO":          "schedule",
    "RecurringMessageScheduleDTO": "schedule",
    "TimeBucket":                  "schedule",

    # configuration.proto
    "GlobalConfigurationDTO":   "configuration",
    "ConfigurationEventDTO":    "configuration",
    "ReplayProtectionMode":     "configuration",

    # signing_key.proto
    "SigningKeyDTO":                 "signing_key",
    "KeyRole":                       "signing_key",
    "CommandTrustVerificationResult":"signing_key",

    # topic_meta.proto
    "TopicMetaDTO":   "topic_meta",
    "CleanupPolicy":  "topic_meta",

    # dlq.proto
    "DlqEnvelope":          "dlq",
    "DlqReplayCommand":     "dlq",
    "DlqReplayResult":      "dlq",
    "DlqLineageDTO":        "dlq",
    "DlqReasonCode":        "dlq",
    "DlqSeverity":          "dlq",
    "DlqCaptureStage":      "dlq",
    "DlqEntryDTO":          "dlq",
    "DmnDefinitionsDlqEntryDTO": "dlq",
    "ReplayValidationPolicy": "dlq",

    # process_definition.proto
    "ProcessDefinitionDTO":          "process_definition",
}

# Classes to skip entirely (interfaces, abstract marker classes, etc.)
SKIP_CLASSES = {
    "WithIoMappingDTO", "WithFlowNodeInstancesDTO", "SchedulableMessageDTO",
    "DlqEntryDTO",  # abstract empty base
}

# ─────────────────────────────────────────────
# IMPORTS EACH FILE NEEDS FROM OTHERS
# ─────────────────────────────────────────────
FILE_IMPORTS = {
    "variables":                [],
    "common":                   ["variables"],
    "process_instance":         ["common", "variables"],
    "flow_node_instance":       ["common", "variables", "process_instance", "schedule"],
    "instance_update":          ["common", "variables", "flow_node_instance", "process_instance"],
    "process_instance_trigger": ["common", "variables"],
    "definitions":              ["common", "variables"],
    "dmn_definitions":          ["common"],
    "message_event":            ["common", "variables"],
    "signals":                  ["common"],
    "user_task":                ["common", "variables"],
    "schedule":                 ["common"],
    "configuration":            [],
    "signing_key":              [],
    "topic_meta":               [],
    "dlq":                      [],
    "process_definition":       ["definitions", "common"],
}

# ─────────────────────────────────────────────
# ONEOF DECLARATIONS to inject per file
# (replaces the scattered abstract class hierarchy)
# ─────────────────────────────────────────────
ONEOF_BLOCKS = {
    "flow_node_instance": """\
// FlowNodeInstanceEnvelope — replaces @JsonTypeInfo / FlowNodeInstanceTypeIdResolver
// Field numbers are PERMANENT. Add new types only at the end.
message FlowNodeInstanceEnvelope {
  oneof instance {
    StartEventInstanceMessage       start_event          = 1;  // TypeId "A"
    BoundaryEventInstanceMessage    boundary_event       = 2;  // TypeId "B"
    CallActivityInstanceMessage     call_activity        = 3;  // TypeId "C"
    SendTaskInstanceMessage         send_task            = 4;  // TypeId "D"
    EndEventInstanceMessage         end_event            = 5;  // TypeId "E"
    ScriptTaskInstanceMessage       script_task          = 6;  // TypeId "F"
    MessageEndEventInstanceMessage  msg_end_event        = 7;  // TypeId "G"
    MessageIntermThrowInstanceMessage msg_throw_event    = 8;  // TypeId "H"
    IntermCatchEventInstanceMessage catch_event          = 9;  // TypeId "I"
    EventBasedGwInstanceMessage     ebg                  = 10; // TypeId "J"
    BusinessRuleTaskInstanceMessage brt                  = 11; // TypeId "K"
    MultiInstanceInstanceMessage    multi_instance       = 12; // TypeId "M"
    InclusiveGwInstanceMessage      inclusive_gw         = 13; // TypeId "N"
    ParallelGwInstanceMessage       parallel_gw          = 14; // TypeId "P"
    ReceiveTaskInstanceMessage      receive_task         = 15; // TypeId "R"
    SubProcessInstanceMessage       sub_process          = 16; // TypeId "S"
    TaskInstanceMessage             task                 = 17; // TypeId "T"
    UserTaskInstanceMessage         user_task            = 18; // TypeId "U"
    ServiceTaskInstanceMessage      service_task         = 19; // TypeId "V"
    IntermThrowEventInstanceMessage throw_event          = 20; // TypeId "W"
    ExclusiveGwInstanceMessage      exclusive_gw         = 21; // TypeId "X"
  }
}
""",

    "instance_update": """\
// InstanceUpdateEnvelope — replaces InstanceUpdateTypeIdResolver
message InstanceUpdateEnvelope {
  oneof update {
    FlowNodeInstanceUpdateMessage   flow_node = 1;  // TypeId "F"
    ProcessInstanceUpdateMessage    process   = 2;  // TypeId "P"
  }
}
""",

    "process_instance_trigger": """\
// ProcessInstanceTriggerEnvelope — replaces ProcessInstanceTriggerTypeIdResolver
message ProcessInstanceTriggerEnvelope {
  oneof trigger {
    StartCommandMessage               start              = 1; // TypeId "A"
    ContinueFlowElementTriggerMessage continue_flow      = 2; // TypeId "C"
    ExternalTaskTriggerMessage        external_task      = 3; // TypeId "E"
    SetVariableTriggerMessage         set_variable       = 4; // TypeId "F"
    ExternalTaskResponseTriggerMessage ext_task_response = 5; // TypeId "R"
    StartFlowElementTriggerMessage    start_flow_element = 6; // TypeId "S"
    AbortTriggerMessage               abort              = 7; // TypeId "T"
    UserTaskResponseTriggerMessage    user_task_response = 8; // TypeId "U"
    EventSignalTriggerMessage         event_signal       = 9; // TypeId "V"
  }
}

// EventSignalEnvelope — replaces EventSignalTypeIdResolver
message EventSignalEnvelope {
  oneof signal {
    MessageEventSignalMessage   message_signal    = 1; // TypeId "M"
    ErrorEventSignalMessage     error_signal      = 2; // TypeId "R"
    EscalationEventSignalMessage escalation_signal = 3; // TypeId "S"
    TimerEventSignalMessage     timer_signal      = 4; // TypeId "T"
    SignalEventSignalMessage     signal_signal     = 5; // TypeId "I"
  }
}
""",

    "definitions": """\
// BaseElementEnvelope — replaces BaseElementTypeIdResolver (27 types)
message BaseElementEnvelope {
  oneof element {
    BoundaryEventMessage              boundary_event        = 1;  // TypeId "B"
    StartEventMessage                 start_event           = 2;  // TypeId "S"
    IntermediateCatchEventMessage     catch_event           = 3;  // TypeId "IC"
    IntermediateThrowEventMessage     throw_event           = 4;  // TypeId "IT"
    EndEventMessage                   end_event             = 5;  // TypeId "E"
    InclusiveGatewayMessage           inclusive_gw          = 6;  // TypeId "IG"
    EventBasedGatewayMessage          event_based_gw        = 7;  // TypeId "VG"
    ParallelGatewayMessage            parallel_gw           = 8;  // TypeId "PG"
    ExclusiveGatewayMessage           exclusive_gw          = 9;  // TypeId "EG"
    SubProcessMessage                 sub_process           = 10; // TypeId "SP"
    CallActivityMessage               call_activity         = 11; // TypeId "CA"
    ReceiveTaskMessage                receive_task          = 12; // TypeId "RT"
    SendTaskMessage                   send_task             = 13; // TypeId "ST"
    ServiceTaskMessage                service_task          = 14; // TypeId "SV"
    MessageEndEventMessage            msg_end_event         = 15; // TypeId "MS"
    MessageIntermThrowEventMessage    msg_throw_event       = 16; // TypeId "MI"
    BusinessRuleTaskMessage           brt                   = 17; // TypeId "BR"
    ScriptTaskMessage                 script_task           = 18; // TypeId "SC"
    UserTaskMessage                   user_task             = 19; // TypeId "UT"
    TaskMessage                       task                  = 20; // TypeId "T"
    SequenceFlowMessage               sequence_flow         = 21; // TypeId "Q"
    ProcessMessage                    process               = 22; // TypeId "P"
    LinkEventDefinitionMessage        link_event_def        = 23; // TypeId "LE"
    TerminateEventDefinitionMessage   terminate_event_def   = 24; // TypeId "TE"
    EscalationEventDefinitionMessage  escalation_event_def  = 25; // TypeId "ES"
    TimerEventDefinitionMessage       timer_event_def       = 26; // TypeId "TM"
    ErrorEventDefinitionMessage       error_event_def       = 27; // TypeId "ER"
    MessageEventDefinitionMessage     msg_event_def         = 28; // TypeId "ME"
    SignalEventDefinitionMessage      signal_event_def      = 29; // TypeId "SE"
  }
}

// DefinitionsTriggerEnvelope — replaces DefinitionsTriggerTypeIdResolver
message DefinitionsTriggerEnvelope {
  oneof trigger {
    XmlDefinitionsMessage             xml_defs     = 1; // TypeId "X"
    ParsedDefinitionsMessage          parsed_defs  = 2; // TypeId "P"
    ProcessDefinitionActivationMessage activation  = 3; // TypeId "A"
  }
}
""",

    "message_event": """\
// MessageEventEnvelope — replaces MessageEventTypeIdResolver
message MessageEventEnvelope {
  oneof event {
    DefinitionMessageSubscriptionMessage        def_sub         = 1; // TypeId "D"
    CancelDefinitionMessageSubscriptionMessage  cancel_def_sub  = 2; // TypeId "C"
    CorrelationMessageSubscriptionMessage       corr_sub        = 3; // TypeId "O"
    CancelCorrelationMessageSubscriptionMessage cancel_corr_sub = 4; // TypeId "A"
    DefinitionMessageEventTriggerMessage        def_trigger     = 5; // TypeId "E"
    CorrelationMessageEventTriggerMessage       corr_trigger    = 6; // TypeId "R"
  }
}
""",

    "signals": """\
// SignalEnvelope — replaces SignalTypeIdResolver
message SignalEnvelope {
  oneof signal {
    SignalMessage                        signal              = 1; // TypeId "S"
    NewInstanceSignalSubscriptionMessage new_instance_sub    = 2; // TypeId "NS"
    CancelInstanceSignalSubscriptionMessage cancel_instance_sub = 3; // TypeId "CS"
    NewDefinitionSignalSubscriptionMessage new_def_sub       = 4; // TypeId "ND"
    CancelDefinitionSignalSubscriptionMessage cancel_def_sub = 5; // TypeId "CD"
  }
}
""",

    "process_instance": """\
// SubscriptionEnvelope — replaces SubscriptionTypeIdResolver
message SubscriptionEnvelope {
  oneof subscription {
    CatchAllErrorSubscriptionMessage    catch_all_error    = 1; // TypeId "A"
    ErrorSubscriptionMessage            error_sub          = 2; // TypeId "B"
    CatchAllEscalationSubscriptionMessage catch_all_esc    = 3; // TypeId "C"
    EscalationSubscriptionMessage       escalation_sub     = 4; // TypeId "D"
    MessageSubscriptionMessage          message_sub        = 5; // TypeId "E"
    TimerSubscriptionMessage            timer_sub          = 6; // TypeId "F"
    SignalSubscriptionMessage           signal_sub         = 7; // TypeId "S"
  }
}
""",

    "schedule": """\
// ScheduleKeyEnvelope — replaces ScheduleKeyTypeIdResolver
message ScheduleKeyEnvelope {
  oneof key {
    DefinitionScheduleKeyMessage definition_key = 1; // TypeId "D"
    InstanceScheduleKeyMessage   instance_key   = 2; // TypeId "I"
  }
}

// MessageScheduleEnvelope — replaces MessageSchedulerTypeIdResolver
message MessageScheduleEnvelope {
  oneof schedule {
    RecurringMessageScheduleMessage recurring    = 1; // TypeId "R"
    FixedRateMessageScheduleMessage fixed_rate   = 2; // TypeId "F"
    OneTimeScheduleMessage          one_time     = 3; // TypeId "O"
  }
}
""",
}

# ─────────────────────────────────────────────
# PARSER
# ─────────────────────────────────────────────
FIELD_RE = re.compile(
    r'(?:private|protected)\s+'
    r'(?:final\s+)?'
    r'([\w<>,\s\[\]]+?)\s+'   # type  (group 1)
    r'(\w+)\s*;',              # name  (group 2)
    re.MULTILINE,
)
CLASS_RE = re.compile(
    r'(?:public\s+)?(?:abstract\s+)?(?:class|interface|enum)\s+'
    r'(\w+)'                                  # class name  (group 1)
    r'(?:\s+extends\s+([\w<>,.?\s]+?))?'      # extends     (group 2)
    r'(?:\s+implements\s+([\w<>,.?\s]+?))?'   # implements  (group 3)
    r'\s*\{',
    re.MULTILINE,
)
ENUM_CONST_RE  = re.compile(r'^\s{2}(\w+)\s*(?:\(.*?\))?\s*[,;]', re.MULTILINE)


def strip_generics(t: str) -> str:
    return re.sub(r'<.*>', '', t).strip()


def resolve_proto_type(java_type: str) -> str:
    """Map a Java type string to a proto field type string."""
    java_type = java_type.strip()

    # List<X> / Set<X>  →  repeated X
    m = re.match(r'(?:List|Set)<(.+)>', java_type)
    if m:
        inner = m.group(1).strip()
        return f"repeated {resolve_proto_type(inner)}"

    # Map<K, V>
    m = re.match(r'Map<(.+),\s*(.+)>', java_type)
    if m:
        k = resolve_proto_type(m.group(1).strip())
        v = resolve_proto_type(m.group(2).strip())
        return f"map<{k}, {v}>"

    bare = strip_generics(java_type)

    if bare in TYPE_MAP:
        return TYPE_MAP[bare]

    # DTO → message name  (FooDTO → FooMessage)
    if bare.endswith("DTO"):
        return bare[:-3] + "Message"

    # Enum names (stay as-is in proto, but we need to use snake_case message ref)
    if bare in ENUM_NAMES:
        return bare  # proto enum name == Java enum name

    # Fallback: keep as-is (e.g. already a proto name, or primitive we missed)
    return bare


def parse_java_file(path: Path):
    """Returns dict with keys: name, is_enum, is_abstract, is_interface,
    parent, fields[(name, java_type)], enum_consts."""
    src = path.read_text(encoding="utf-8")

    # Remove comments
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    src = re.sub(r'//[^\n]*', '', src)

    m = CLASS_RE.search(src)
    if not m:
        return None

    class_name = m.group(1)
    parent_raw = (m.group(2) or "").strip()
    parent = strip_generics(parent_raw.split(",")[0]).strip() if parent_raw else ""

    is_enum      = bool(re.search(r'\benum\b\s+' + re.escape(class_name), src))
    is_abstract  = bool(re.search(r'\babstract\b', src[:m.start() + 60]))
    is_interface = bool(re.search(r'\binterface\b\s+' + re.escape(class_name), src))

    enum_consts = []
    if is_enum:
        enum_consts = ENUM_CONST_RE.findall(src)

    fields = []
    if not is_enum and not is_interface:
        for fm in FIELD_RE.finditer(src):
            ftype = fm.group(1).strip()
            fname = fm.group(2).strip()
            # skip logger, static, OBJECT_MAPPER etc.
            if fname[0].isupper():
                continue
            if ftype.startswith("static"):
                continue
            fields.append((fname, ftype))

    return {
        "name":         class_name,
        "is_enum":      is_enum,
        "is_abstract":  is_abstract,
        "is_interface": is_interface,
        "parent":       parent,
        "fields":       fields,
        "enum_consts":  enum_consts,
    }


# ─────────────────────────────────────────────
# COLLECTOR — walk all DTO Java files
# ─────────────────────────────────────────────
def collect_dtos(dto_dir: Path, subpkg_dir: Path):
    classes = {}
    for p in list(dto_dir.glob("*.java")) + list(subpkg_dir.glob("*.java")):
        info = parse_java_file(p)
        if info:
            classes[info["name"]] = info
    return classes


# ─────────────────────────────────────────────
# FLATTENING — inline inherited fields
# ─────────────────────────────────────────────
def flatten_fields(class_name: str, classes: dict, visited=None) -> list:
    if visited is None:
        visited = set()
    if class_name in visited:
        return []
    visited.add(class_name)

    info = classes.get(class_name)
    if not info:
        return []

    parent_fields = []
    if info["parent"] and info["parent"] != "Object":
        parent_fields = flatten_fields(info["parent"], classes, visited)

    return parent_fields + info["fields"]


# ─────────────────────────────────────────────
# PROTO GENERATOR
# ─────────────────────────────────────────────
def camel_to_snake(name: str) -> str:
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()


def class_to_proto_name(name: str) -> str:
    """FooBarDTO → FooBarMessage   |   FooBar → FooBar"""
    if name.endswith("DTO"):
        return name[:-3] + "Message"
    return name


def build_message(class_name: str, classes: dict) -> str:
    info = classes[class_name]
    if class_name in SKIP_CLASSES:
        return f"// {class_name} — skipped (interface / marker)\n"

    proto_name = class_to_proto_name(class_name)
    fields = flatten_fields(class_name, classes)

    lines = [f"message {proto_name} {{"]
    if not fields:
        lines.append("  // no own fields (abstract base or marker)")
    for idx, (fname, ftype) in enumerate(fields, start=1):
        ptype = resolve_proto_type(ftype)
        pname = camel_to_snake(fname)
        lines.append(f"  {ptype} {pname} = {idx};")
    lines.append("}")
    return "\n".join(lines) + "\n"


def build_enum(class_name: str, info: dict) -> str:
    lines = [f"enum {class_name} {{"]
    for idx, const in enumerate(info["enum_consts"]):
        lines.append(f"  {const} = {idx};")
    lines.append("}")
    return "\n".join(lines) + "\n"


# ─────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────
def main():
    dry_run = "--dry-run" in sys.argv

    classes = collect_dtos(DTO_DIR, SUBPKG)
    print(f"Parsed {len(classes)} Java classes/enums from DTO directories.")

    # Group by target proto file
    groups = defaultdict(list)
    unassigned = []
    for name, info in sorted(classes.items()):
        target = FILE_ASSIGNMENT.get(name)
        if target:
            groups[target].append(name)
        else:
            unassigned.append(name)

    if unassigned:
        print(f"\nWARNING — {len(unassigned)} class(es) not assigned to any proto file:")
        for u in unassigned:
            print(f"  {u}")

    # Build and write each proto file
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for proto_file, class_names in sorted(groups.items()):
        outer = "".join(w.capitalize() for w in proto_file.split("_")) + "Proto"
        imports_list = FILE_IMPORTS.get(proto_file, [])
        import_lines = "\n".join(
            f'import "io/taktx/proto/{imp}.proto";' for imp in imports_list
        )

        body_parts = []

        # Inject oneof envelope blocks first (before individual messages)
        if proto_file in ONEOF_BLOCKS:
            body_parts.append(ONEOF_BLOCKS[proto_file])

        # Emit enums first, then messages
        enums    = [n for n in class_names if classes[n]["is_enum"]]
        messages = [n for n in class_names if not classes[n]["is_enum"]
                    and not classes[n]["is_interface"]
                    and n not in SKIP_CLASSES]

        for name in enums:
            body_parts.append(build_enum(name, classes[name]))

        for name in messages:
            body_parts.append("// Java: " + name
                               + (" (abstract)" if classes[name]["is_abstract"] else ""))
            body_parts.append(build_message(name, classes))

        proto_content = (
            PROTO_HEADER.format(
                pkg=PROTO_PACKAGE,
                jpkg=JAVA_PACKAGE,
                outer=outer,
            )
            + (("\n" + import_lines + "\n") if import_lines else "")
            + "\n"
            + "\n".join(body_parts)
        )

        out_path = OUT_DIR / f"{proto_file}.proto"
        if dry_run:
            print(f"\n{'='*60}\n{out_path}\n{'='*60}")
            print(proto_content[:2000], "..." if len(proto_content) > 2000 else "")
        else:
            out_path.write_text(proto_content, encoding="utf-8")
            print(f"  Written {out_path.relative_to(REPO_ROOT)}"
                  f"  ({len(proto_content):,} bytes)")

    # Write the hand-crafted files that have no DTO equivalents
    _write_variables_proto(dry_run)
    _write_common_proto(dry_run)

    print("\nDone.  Review each .proto file, finalize field numbers, then run PROTO-1.2.")


# ─────────────────────────────────────────────
# HAND-CRAFTED STUBS (variables, common)
# These cannot be auto-generated from DTOs because
# they introduce new proto-only types.
# ─────────────────────────────────────────────
def _write_variables_proto(dry_run: bool):
    content = '''\
syntax = "proto3";

package io.taktx.proto;

option java_package = "io.taktx.proto";
option java_multiple_files = true;
option java_outer_classname = "VariablesProto";

// Replaces VariablesDTO / Map<String,JsonNode> throughout the codebase.
// Uses sint64 for integer values (zigzag varint — efficient for small numbers).
// null_value=true encodes JSON null. Default proto values (false/0/"") are omitted on the wire.
message VariableValue {
  oneof kind {
    bool          null_value   = 1;
    bool          bool_value   = 2;
    sint64        long_value   = 3;
    double        double_value = 4;
    string        string_value = 5;
    VarMap        map_value    = 6;
    VarList       list_value   = 7;
    bytes         bytes_value  = 8;
  }
}

// Replaces VariablesDTO (Map<String, JsonNode>)
message VarMap {
  map<string, VariableValue> entries = 1;
}

// Replaces List<JsonNode>
message VarList {
  repeated VariableValue items = 1;
}
'''
    _write_or_print("variables", content, dry_run)


def _write_common_proto(dry_run: bool):
    content = '''\
syntax = "proto3";

package io.taktx.proto;

option java_package = "io.taktx.proto";
option java_multiple_files = true;
option java_outer_classname = "CommonProto";

import "io/taktx/proto/variables.proto";

// UUID encoded as two fixed64 (MSB first).
// Replaces java.util.UUID everywhere on the wire.
// 16 bytes vs. ~36 bytes for a hyphenated string UUID.
message Uuid {
  fixed64 high = 1;
  fixed64 low  = 2;
}

// Embedded key value — NOT the range-queryable Kafka Streams store key.
// Replaces ProcessDefinitionKey when embedded inside a value message.
message ProcessDefinitionKeyMessage {
  string process_definition_id = 1;
  int32  version               = 2; // -1 = "latest"
}

// Replaces DefinitionsKey (processDefinitionId + content hash)
message DefinitionsKeyMessage {
  string process_definition_id = 1;
  string hash                  = 2;
}

// Replaces DmnDefinitionsKey
message DmnDefinitionsKeyMessage {
  string dmn_definition_id = 1;
  string hash              = 2;
}

// Replaces IncidentInfoDTO
message IncidentInfoMessage {
  repeated sint64 element_instance_id_path = 1;
  string          message                  = 2;
  repeated string stacktrace               = 3;
  string          dlq_entry_ref            = 4; // nullable: empty = absent
}

// Replaces IoVariableMappingDTO (source/target FEEL expression pair)
message IoVariableMappingMessage {
  string source = 1;
  string target = 2;
}

// Replaces InputOutputMappingDTO
message InputOutputMappingMessage {
  repeated IoVariableMappingMessage input_mappings  = 1;
  repeated IoVariableMappingMessage output_mappings = 2;
}

// Replaces FlowConditionDTO
message FlowConditionMessage {
  string expression = 1;
}

// Replaces ExecutionState enum
// Codes: INITIALIZED="S", ACTIVE="A", COMPLETED="C", ABORTED="F"
enum ExecutionState {
  EXECUTION_STATE_UNSPECIFIED = 0;
  EXECUTION_STATE_INITIALIZED = 1; // "S"
  EXECUTION_STATE_ACTIVE      = 2; // "A"
  EXECUTION_STATE_COMPLETED   = 3; // "C"
  EXECUTION_STATE_ABORTED     = 4; // "F"
}
'''
    _write_or_print("common", content, dry_run)


def _write_or_print(name: str, content: str, dry_run: bool):
    out_path = OUT_DIR / f"{name}.proto"
    if dry_run:
        print(f"\n{'='*60}\n{out_path.name}\n{'='*60}")
        print(content[:1500])
    else:
        out_path.write_text(content, encoding="utf-8")
        print(f"  Written {out_path.name}  ({len(content):,} bytes)")


if __name__ == "__main__":
    main()

