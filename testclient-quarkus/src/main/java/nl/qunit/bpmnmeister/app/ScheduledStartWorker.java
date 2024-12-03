package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.client.BpmnDeployment;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/scheduled_start_10s.bpmn")
public class ScheduledStartWorker {}
