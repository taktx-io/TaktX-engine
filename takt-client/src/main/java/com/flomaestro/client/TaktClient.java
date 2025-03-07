package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class TaktClient {

  private final ProcessDefinitionConsumer processDefinitionConsumer;
  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ExternalTasksForProcessDefinitionConsumer externalTaskConsumer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

  public TaktClient(String tenant, String namespace, String kafkaBootstrapServers)
      throws IOException {
    TaktPropertiesHelper taktPropertiesHelper =
        new TaktPropertiesHelper(tenant, namespace, kafkaBootstrapServers);
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer = new ProcessInstanceProducer(taktPropertiesHelper);
    this.processInstanceUpdateConsumer =
        new ProcessInstanceUpdateConsumer(taktPropertiesHelper, executor);
    this.externalTaskConsumer =
        new ExternalTasksForProcessDefinitionConsumer(taktPropertiesHelper, executor);
  }

  public static TaktClientBuilder newClientBuilder() {
    return new TaktClientBuilder();
  }

  public void start() throws IOException {
    this.processDefinitionConsumer.subscribeToDefinitionRecords();
    this.processDefinitionConsumer.subscribeToProcessDefinitionUpdates(this.externalTaskConsumer);
  }

  public void stop() {
    this.processDefinitionConsumer.stop();
    this.externalTaskConsumer.stop();
  }

  public ParsedDefinitionsDTO deployProcessDefinition(InputStream inputStream) throws IOException {
    return this.processDefinitionDeployer.deploy(new String(inputStream.readAllBytes()));
  }

  public Optional<ProcessDefinitionDTO> getProcessDefinitionByHash(
      String processDefinitionId, String hash) {
    return this.processDefinitionConsumer.getDeployedProcessDefinitionbyHash(
        processDefinitionId, hash);
  }

  public void registerExternalTaskTriggerConsumer(
      String processDefinitionId, BiConsumer<UUID, ExternalTaskTriggerDTO> consumer) {
    this.externalTaskConsumer.registerExternalTaskTriggerConsumer(processDefinitionId, consumer);
  }

  public UUID startProcess(String process, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, variables);
  }

  public void registerInstanceUpdateConsumer(BiConsumer<UUID, InstanceUpdateDTO> consumer) {
    this.processInstanceUpdateConsumer.addInstanceUpdateConsumer(consumer);
  }

  public static class TaktClientBuilder {

    private String tenant;
    private String namespace;
    private String kafkaBootstrapServers;

    private TaktClientBuilder() {
      this.tenant = System.getenv("TENANT");
      this.namespace = System.getenv("NAMESPACE");
      this.kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
    }

    public TaktClient build() throws IOException {
      if (tenant == null) {
        throw new IllegalArgumentException("TENANT environment variable is not set");
      }
      if (namespace == null) {
        throw new IllegalArgumentException("NAMESPACE environment variable is not set");
      }
      if (kafkaBootstrapServers == null) {
        throw new IllegalArgumentException(
            "KAFKA_BOOTSTRAP_SERVERS environment variable is not set");
      }
      return new TaktClient(tenant, namespace, kafkaBootstrapServers);
    }

    public TaktClientBuilder withTenant(String tenant) {
      this.tenant = tenant;
      return this;
    }

    public TaktClientBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public TaktClientBuilder withBootstrapServers(String kafkaBootstrapServers) {
      this.kafkaBootstrapServers = kafkaBootstrapServers;
      return this;
    }
  }
}
