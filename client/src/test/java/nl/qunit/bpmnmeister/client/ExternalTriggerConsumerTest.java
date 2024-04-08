package nl.qunit.bpmnmeister.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Variables;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExternalTriggerConsumerTest {
    @Inject
    ExternalTriggerConsumer externalTriggerConsumer;
    @InjectMock
    private Deployer deployer;

    @Test
    void testConsume() throws InvocationTargetException, IllegalAccessException {
        ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
        ProcessDefinitionKey processDefinitionKey = new ProcessDefinitionKey(new String("processDefinitionId"),  1);
        String externalTaskId = new String("externalTaskId");
        JsonNode jsonNode = new TextNode("testvalue");
        Variables variables = new Variables(Map.of("variable1", jsonNode));
        ExternalTaskTrigger mockExternalTaskTrigger = new ExternalTaskTrigger(processInstanceKey, processDefinitionKey, externalTaskId, variables);

        TestWorker testWorker = new TestWorker();
        when(deployer.getDefinitionMap()).thenReturn(Map.of(new String("processDefinitionId"), Map.of(1, testWorker)));

        // Call the method to test
        externalTriggerConsumer.consume(mockExternalTaskTrigger);

        // Assert that the method was called with the correct parameters
        assertThat(testWorker.externalTaskTrigger).isEqualTo(mockExternalTaskTrigger);
        assertThat(testWorker.variable1).isEqualTo("testvalue");
    }


    static class TestWorker {
        private ExternalTaskTrigger externalTaskTrigger;
        private String variable1;

        @ExternalTask(element = "externalTaskId")
        public void testMethod(ExternalTaskTrigger externalTaskTrigger, String variable1) {
            // Do something
            this.externalTaskTrigger = externalTaskTrigger;
            this.variable1 = variable1;
        }
    }
}