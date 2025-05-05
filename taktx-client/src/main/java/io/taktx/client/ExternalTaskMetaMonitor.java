package io.taktx.client;

import io.taktx.dto.ExternalTaskMetaDTO;
import io.taktx.dto.ExternalTaskMetaState;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class ExternalTaskMetaMonitor implements ExternalTaskMetaConsumer {
  private final Map<String, ExternalTaskMetaDTO> metaMap = new ConcurrentHashMap<>();
  private final Set<String> jobIdsToMonitor;
  private final Consumer<Void> callback;

  public ExternalTaskMetaMonitor(Set<String> jobIdsToMonitor, Consumer<Void> callback) {
    this.jobIdsToMonitor = jobIdsToMonitor;
    this.callback = callback;
  }

  @Override
  public void accept(String jobId, ExternalTaskMetaDTO meta) {
    if (meta == null) {
      metaMap.remove(jobId);
    } else {
      metaMap.put(jobId, meta);
    }
    // Notify the callback if all jobIdsToMonitor are present and have meta state CREATED
    if (jobIdsToMonitor.stream().allMatch(metaMap::containsKey)) {
      boolean allCreated = jobIdsToMonitor.stream()
          .allMatch(id -> metaMap.get(id).getState() == ExternalTaskMetaState.CREATED);
      if (allCreated) {
        callback.accept(null);
      }
    }

  }

}
