package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.client.BpmnDeployment;

@BpmnDeployment(resource = "bpmn/scheduled_start_1minute.bpmn")
@ApplicationScoped
@Startup
public class ScheduledStartWorker {}
