package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.client.BpmnDeployment;

@BpmnDeployment(resource = "bpmn/scheduled_start_10s.bpmn")
@ApplicationScoped
@Startup
public class ScheduledStartWorker {}
