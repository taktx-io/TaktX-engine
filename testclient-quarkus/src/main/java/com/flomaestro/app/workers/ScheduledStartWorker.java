package com.flomaestro.app.workers;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
// @BpmnDeployment(resource = "bpmn/scheduled_start_10s.bpmn")
public class ScheduledStartWorker {}
