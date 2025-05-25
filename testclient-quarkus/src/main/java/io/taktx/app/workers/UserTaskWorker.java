package io.taktx.app.workers;

import io.quarkus.runtime.Startup;
import io.taktx.client.annotation.TaktDeployment;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "bpmn/usertask.bpmn")
public class UserTaskWorker {}
