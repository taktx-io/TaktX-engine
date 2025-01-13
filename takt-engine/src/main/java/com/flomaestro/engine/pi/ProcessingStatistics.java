package com.flomaestro.engine.pi;

import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

@Singleton
@Getter
public class ProcessingStatistics {
  private final AtomicInteger totalProcessInstancesStarted = new AtomicInteger(0);
  private final AtomicInteger totalProcessInstancesFinished = new AtomicInteger(0);
  private final AtomicInteger processInstancesStarted = new AtomicInteger(0);
  private final AtomicInteger processInstancesFinished = new AtomicInteger(0);
  private final AtomicInteger flowNodesStarted = new AtomicInteger(0);
  private final AtomicInteger flowNodesContinued = new AtomicInteger(0);
  private final AtomicInteger flowNodesFinished = new AtomicInteger(0);
  private final AtomicInteger requestsReceived = new AtomicInteger(0);
  private final AtomicInteger requestToProcessingLatencyMs = new AtomicInteger(0);

  void reset() {
    processInstancesStarted.set(0);
    processInstancesFinished.set(0);
    flowNodesStarted.set(0);
    flowNodesContinued.set(0);
    flowNodesFinished.set(0);
    requestsReceived.set(0);
    requestToProcessingLatencyMs.set(0);
  }

  public void increaseProcessInstancesStarted() {
    totalProcessInstancesStarted.incrementAndGet();
    processInstancesStarted.incrementAndGet();
  }

  public void increaseProcessInstancesFinished() {
    totalProcessInstancesFinished.incrementAndGet();
    processInstancesFinished.incrementAndGet();
  }

  public void increaseFlowNodesStarted() {
    flowNodesStarted.incrementAndGet();
  }

  public void increaseFlowNodesFinished() {
    flowNodesFinished.incrementAndGet();
  }

  public void increaseRequestsReceived() {
    requestsReceived.incrementAndGet();
  }

  public void addRequestToProcessingLatency(long latency) {
    requestToProcessingLatencyMs.addAndGet((int) latency);
  }

  public void increaseFlowNodesContinued() {
    flowNodesContinued.incrementAndGet();
  }

  public ProcessingStatisticsDTO toDTO() {
    ProcessingStatisticsDTO statisticsDTO = new ProcessingStatisticsDTO();
    statisticsDTO.setTotalProcessInstancesFinished(totalProcessInstancesFinished.get());
    statisticsDTO.setTotalProcessInstancesStarted(totalProcessInstancesStarted.get());
    statisticsDTO.setFlowNodesFinished(flowNodesFinished.get());
    statisticsDTO.setFlowNodesContinued(flowNodesContinued.get());
    statisticsDTO.setFlowNodesStarted(flowNodesStarted.get());
    statisticsDTO.setProcessInstancesFinished(processInstancesFinished.get());
    statisticsDTO.setProcessInstancesStarted(processInstancesStarted.get());
    statisticsDTO.setAverageRequestLatencyMs(
        requestsReceived.get() == 0
            ? 0
            : (float) requestToProcessingLatencyMs.get() / requestsReceived.get());
    return statisticsDTO;
  }
}
