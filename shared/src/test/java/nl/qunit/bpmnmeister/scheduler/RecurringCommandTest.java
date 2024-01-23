package nl.qunit.bpmnmeister.scheduler;

import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RecurringCommandTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2021-01-01T00:00:30.00Z"), Clock.systemDefaultZone().getZone());
    private static final Clock CLOCK_AFTER = Clock.fixed(Instant.parse("2021-01-01T00:01:00.01Z"), Clock.systemDefaultZone().getZone());
    private static final Clock CLOCK_AFTER2 = Clock.fixed(Instant.parse("2021-01-01T00:01:59.999Z"), Clock.systemDefaultZone().getZone());
    private static final Clock CLOCK_AFTER3 = Clock.fixed(Instant.parse("2021-01-01T00:02:00.001Z"), Clock.systemDefaultZone().getZone());

    @Test
    void test() {
        ProcessInstanceTrigger trigger1 = mock(ProcessInstanceTrigger.class);
        RecurringCommand recurringCommand = new RecurringCommand(List.of(trigger1), "0 * * * * ?", Instant.now(CLOCK).toString());

        List<ProcessInstanceTrigger> triggersReceived = new ArrayList<>();
        RecurringCommand newCommand = recurringCommand.evaluate(Instant.now(CLOCK), triggers -> {
            triggersReceived.addAll(triggers);
        });

        assertThat(triggersReceived).isEmpty();
        assertThat(newCommand.getInstantiation()).isEqualTo("2021-01-01T00:00:30Z");
        assertThat(newCommand.getTriggers()).containsExactly(trigger1);
        assertThat(newCommand.getCron()).isEqualTo(recurringCommand.getCron());
    }
    @Test
    void testAfter() {
        ProcessInstanceTrigger trigger1 = mock(ProcessInstanceTrigger.class);
        RecurringCommand recurringCommand = new RecurringCommand(List.of(trigger1), "0 * * * * ?", Instant.now(CLOCK).toString());

        List<ProcessInstanceTrigger> triggersReceived = new ArrayList<>();
        RecurringCommand newCommand = recurringCommand.evaluate(Instant.now(CLOCK_AFTER), triggersReceived::addAll);

        assertThat(triggersReceived).containsExactly(trigger1);
        assertThat(newCommand.getInstantiation()).isEqualTo("2021-01-01T00:01Z");
        assertThat(newCommand.getTriggers()).containsExactly(trigger1);
        assertThat(newCommand.getCron()).isEqualTo(recurringCommand.getCron());

        triggersReceived.clear();
        RecurringCommand newCommand2 = newCommand.evaluate(Instant.now(CLOCK_AFTER2), triggersReceived::addAll);

        assertThat(triggersReceived).isEmpty();
        assertThat(newCommand2.getInstantiation()).isEqualTo("2021-01-01T00:01Z");
        assertThat(newCommand2.getTriggers()).containsExactly(trigger1);
        assertThat(newCommand2.getCron()).isEqualTo(recurringCommand.getCron());

        RecurringCommand newCommand3 = newCommand.evaluate(Instant.now(CLOCK_AFTER3), triggersReceived::addAll);

        assertThat(triggersReceived).containsExactly(trigger1);
        assertThat(newCommand3.getInstantiation()).isEqualTo("2021-01-01T00:02Z");
        assertThat(newCommand3.getTriggers()).containsExactly(trigger1);
        assertThat(newCommand3.getCron()).isEqualTo(recurringCommand.getCron());

    }
}