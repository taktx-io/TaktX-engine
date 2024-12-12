package com.flomaestro.app;

import com.flomaestro.client.BpmnDeployment;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/scheduled_start_10s.bpmn")
public class ScheduledStartWorker {}
