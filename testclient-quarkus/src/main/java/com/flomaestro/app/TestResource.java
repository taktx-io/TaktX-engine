package com.flomaestro.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flomaestro.client.TaktClient;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/processes")
@Slf4j
public class TestResource {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject TaktClient taktClient;

  private final Map<String, Job> jobs = new HashMap<>();

  @GET
  public Set<ProcessDefinitionKey> getProcessDefinitions() {
    return taktClient.getProcessDefinitionConsumer().getDeployedProcessDefinitions();
  }

  @POST
  @Path("/{process}/{count}")
  public void startProcessInstance(
      @PathParam("process") String process,
      @PathParam("count") int count,
      @RequestBody String payload)
      throws JsonProcessingException {
    JsonNode jsonNode = objectMapper.readTree(payload);

    VariablesDTO variables = VariablesDTO.empty();
    jsonNode.fieldNames().forEachRemaining(key -> variables.put(key, jsonNode.get(key)));
    for (int i = 0; i < count; i++) {
      taktClient.startProcess(process, variables);
    }
  }

  @POST
  @Path("/jobs/{process}/{count}")
  public Job startJob(
      @PathParam("process") String process,
      @PathParam("count") int count,
      @RequestBody String payload)
      throws JsonProcessingException {

    JsonNode jsonNode = objectMapper.readTree(payload);
    VariablesDTO variables = VariablesDTO.empty();
    jsonNode.fieldNames().forEachRemaining(key -> variables.put(key, jsonNode.get(key)));

    Job job = new Job(process, count, variables);
    jobs.put(job.process, job);
    return job;
  }

  @DELETE
  @Path("/jobs/{process}")
  public Job startJob(@PathParam("process") String process) {
    return jobs.remove(process);
  }

  @GET
  @Path("/jobs")
  public Set<Job> getJobs() {
    return Set.copyOf(jobs.values());
  }

  @Scheduled(every = "1s")
  void increment() {
    jobs.forEach(
        (key, value) -> {
          log.info("Starting {} jobs {}", value.count, value.process);
          for (int i = 0; i < value.count; i++) {
            taktClient.startProcess(value.process, value.variables);
          }
        });
  }

  private record Job(String process, int count, VariablesDTO variables) {}
}
