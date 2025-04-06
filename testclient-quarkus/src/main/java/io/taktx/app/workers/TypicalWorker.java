package io.taktx.app.workers;

import io.quarkus.runtime.Startup;
import io.taktx.client.ExternalTaskInstanceResponder;
import io.taktx.client.annotation.TaktDeployment;
import io.taktx.client.annotation.TaktWorker;
import io.taktx.client.annotation.TaktWorkerMethod;
import io.taktx.client.annotation.Variable;
import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "bpmn/typical.bpmn")
@TaktWorker(processDefinitionId = "benchmark")
public class TypicalWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @TaktWorkerMethod(taskId = "benchmark-task-200", autoComplete = false)
  public void doWork(
      ExternalTaskInstanceResponder externalTaskInstanceResponder,
      ExternalTaskTriggerDTO externalTaskInstanceDTO,
      Map<String, Object> variables,
      @Variable("varx1") String variable1,
      String varx2) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            Thread.currentThread().interrupt();
          }

          externalTaskInstanceResponder.respondSuccess(Map.of("result", "success"));
        });
  }

  @TaktWorkerMethod(taskId = "benchmark-task-200-completed")
  public boolean doWorkCompleted() {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return true;
        });
    return true;
  }
}
