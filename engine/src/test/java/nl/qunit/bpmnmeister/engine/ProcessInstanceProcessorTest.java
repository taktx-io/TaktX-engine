package nl.qunit.bpmnmeister.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import nl.qunit.bpmnmeister.model.processdefinition.*;
import nl.qunit.bpmnmeister.model.processinstance.*;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class ProcessInstanceProcessorTest {

  private static final String PD_ID = "pd-processInstanceId";
  public static final String FLOW_ID_START_TASK_1 = "start_task1";
  public static final String FLOW_ID_TASK_1_TASK_2 = "task1_task2";
  public static final String FLOW_ID_TASK_2_END = "task2_end";
  private static final String START_EVENT_ID = "start";
  private static final String TASK_1_ID = "task1";
  private static final String TASK_2_ID = "task2";
  private static final String TASK_END_ID = "task_end";
  private static final Predicate<ProcessInstance> PREDICATE_TRUE = processInstance -> true;
  private static final StartEvent START_EVENT =
      new StartEvent(START_EVENT_ID, Set.of(FLOW_ID_START_TASK_1));
  private static final Task TASK_1 = new Task(TASK_1_ID, Set.of(FLOW_ID_TASK_1_TASK_2));
  private static final ServiceTask TASK_2 = new ServiceTask(TASK_2_ID, Set.of(FLOW_ID_TASK_2_END));
  private static final Map<String, BpmnElement> BPMN_ELEMENTS =
      Map.of(
          START_EVENT_ID, START_EVENT,
          TASK_1_ID, TASK_1,
          TASK_2_ID, TASK_2);
  private static final Map<String, SequenceFlow> FLOWS =
      Map.of(
          FLOW_ID_START_TASK_1, new SequenceFlow(FLOW_ID_START_TASK_1, TASK_1_ID, PREDICATE_TRUE),
          FLOW_ID_TASK_1_TASK_2, new SequenceFlow(FLOW_ID_TASK_1_TASK_2, TASK_2_ID, PREDICATE_TRUE),
          FLOW_ID_TASK_2_END, new SequenceFlow(FLOW_ID_TASK_2_END, TASK_END_ID, PREDICATE_TRUE));
  private static final ProcessDefinition PROCESS_DEFINITION =
      new ProcessDefinition(PD_ID, BPMN_ELEMENTS, FLOWS);
  private static final UUID PROCESS_INSTANCE_ID = UUID.randomUUID();

  @Test
  void testStartEvent() {

    //    Emitter<ExternalTaskCommand> emitter = Mockito.mock(Emitter.class);
    ProcessInstanceProcessor processor = new ProcessInstanceProcessor();
    ProcessInstance processInstance = new ProcessInstance(PROCESS_INSTANCE_ID, new HashMap<>());
    processor.trigger(
        PROCESS_DEFINITION, processInstance, new Trigger(UUID.randomUUID(), "start", "start"));
    assertThat(processInstance.elementStates().get(START_EVENT_ID))
        .asInstanceOf(InstanceOfAssertFactories.type(StartEventState.class))
        .extracting(StartEventState::state)
        .isEqualTo(StateEnum.FINISHED);
    assertThat(processInstance.elementStates().get(TASK_1_ID))
        .asInstanceOf(InstanceOfAssertFactories.type(TaskState.class))
        .extracting(TaskState::state)
        .isEqualTo(StateEnum.FINISHED);
    assertThat(processInstance.elementStates().get(TASK_2_ID))
        .asInstanceOf(InstanceOfAssertFactories.type(ServiceTaskState.class))
        .extracting(ServiceTaskState::state)
        .isEqualTo(StateEnum.WAITING);
    //    Mockito.verify(emitter).send(new ExternalTaskCommand(TASK_2_ID, PROCESS_INSTANCE_ID));
  }
}
