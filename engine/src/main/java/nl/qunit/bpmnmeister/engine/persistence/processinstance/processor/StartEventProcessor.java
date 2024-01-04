package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.SequenceFlow;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.StartEvent;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class StartEventProcessor extends StateProcessor<StartEvent, StartEventState> {
    final Emitter<RecurringCommand> recurringCommandEmitter;

    public StartEventProcessor(@Channel("recurring-outgoing") Emitter<RecurringCommand> recurringCommandEmitter) {
        this.recurringCommandEmitter = recurringCommandEmitter;
    }

    @Override
    protected TriggerResult triggerWhenInit(Trigger trigger, Definitions processDefinition, StartEvent element, StartEventState oldState) {
        if (element.getTimerEventDefinitions().isEmpty()) {
            // No timerevents, just continue
            return new TriggerResult(StartEventState.builder().build(), element.getOutgoing());
        } else {
            // Schedule the timerevents
            Set<UUID> activeTimerIds = oldState.getActiveTimerIds();
            element
                    .getTimerEventDefinitions()
                    .forEach(
                            timerEventDefinition -> {
                                UUID timerId = UUID.randomUUID();
                                List<Trigger> scheduledTriggers =
                                        element.getOutgoing().stream()
                                                .map(
                                                        flowId -> {
                                                            SequenceFlow flow =
                                                                    (SequenceFlow)
                                                                            processDefinition.getFlowElement(flowId).orElseThrow();
                                                            String targetId = flow.getTarget();
                                                            return new Trigger(trigger.processInstanceId(), targetId, flowId, timerId);
                                                        })
                                                .toList();
                                String timeCycle = timerEventDefinition.getTimeCycle();
                                RecurringCommand msg = new RecurringCommand(timerId, scheduledTriggers, timeCycle);
                                recurringCommandEmitter.send(msg);
                                activeTimerIds.add(timerId);
                            });
            return new TriggerResult(
                    StartEventState.builder().state(StateEnum.ACTIVE).activeTimerIds(activeTimerIds).build(),
                    Set.of());
        }
    }

    @Override
    protected TriggerResult triggerWhenActive(Trigger trigger, Definitions processDefinition, StartEvent element, StartEventState oldState) {
        return new TriggerResult(oldState, element.getOutgoing());
    }

    @Override
    public StartEventState initialState() {
        return StartEventState.builder().state(StateEnum.INIT).activeTimerIds(new HashSet<>()).build();
    }
}
