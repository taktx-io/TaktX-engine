package nl.qunit.bpmnmeister.scheduler;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@Builder
@EqualsAndHashCode
public class ScheduleKey {
    private final ProcessDefinitionKey processDefinitionKey;
    private final ScheduleType scheduleType;
    private final String elementId;
    private final String timerEventDefinitionId;

    @Override
    public String toString() {
        return String.format(
                "%s-%s-%s-%s",
                processDefinitionKey,
                scheduleType,
                elementId,
                timerEventDefinitionId);
    }
}
