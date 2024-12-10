package nl.qunit.bpmnmeister.client;

import java.util.Set;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;

public class TaktClient {
  private final ExternalTriggerConsumer externalTriggerConsumer;
  private final ProcessInstanceProducer processInstanceProducer;

  public TaktClient(String bootstrapServers, String tenant, String namespace) {
    KafkaPropertiesHelper kafkaPropertiesHelper =
        new KafkaPropertiesHelper(bootstrapServers, tenant, namespace);
    this.externalTriggerConsumer = new ExternalTriggerConsumer(kafkaPropertiesHelper);
    this.processInstanceProducer = new ProcessInstanceProducer(kafkaPropertiesHelper);
  }

  public static TaktClientBuilder newClientBuilder() {
    return new TaktClientBuilder();
  }

  public void start() {
    this.externalTriggerConsumer.init();
  }

  public void startProcess(String process) {
    processInstanceProducer.startProcess(process, VariablesDTO.empty());
  }

  public Set<String> getProcessDefinitionConsumers() {
    return externalTriggerConsumer.getProcessDefinitionConsumers();
  }

  public static class TaktClientBuilder {
    private String bootstrapServers;
    private String tenant;
    private String namespace;

    private TaktClientBuilder() {
      this.bootstrapServers = System.getenv("BOOTSTRAP_SERVERS");
      this.tenant = System.getenv("TENANT");
      this.namespace = System.getenv("NAMESPACE");
    }

    public TaktClient build() {
      if (bootstrapServers == null) {
        throw new IllegalArgumentException("BOOTSTRAP_SERVERS environment variable is not set");
      }
      if (tenant == null) {
        throw new IllegalArgumentException("TENANT environment variable is not set");
      }
      if (namespace == null) {
        throw new IllegalArgumentException("NAMESPACE environment variable is not set");
      }
      return new TaktClient(bootstrapServers, tenant, namespace);
    }

    public TaktClientBuilder withBootstrapServers(String bootstrapServers) {
      this.bootstrapServers = bootstrapServers;
      return this;
    }

    public TaktClientBuilder withTenant(String tenant) {
      this.tenant = tenant;
      return this;
    }

    public TaktClientBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }
  }
}
