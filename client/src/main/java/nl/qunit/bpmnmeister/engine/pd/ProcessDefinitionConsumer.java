package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import org.eclipse.microprofile.reactive.messaging.Incoming;

public class ProcessDefinitionConsumer {

  @Incoming("process-definition-parsed-incoming")
  public void consumeParsedProcessDefinition(ProcessDefinition processDefinition) {
    System.out.println("Received process definition: " + processDefinition);
  }
}
