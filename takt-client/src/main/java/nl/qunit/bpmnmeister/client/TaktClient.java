package nl.qunit.bpmnmeister.client;

public class TaktClient {
  private final ExternalTriggerConsumer externalTriggerConsumer;

  public TaktClient(String bootstrapServers, String tenant, String namespace) {
    KafkaPropertiesHelper kafkaPropertiesHelper = new KafkaPropertiesHelper(bootstrapServers, tenant, namespace);
    this.externalTriggerConsumer = new ExternalTriggerConsumer(kafkaPropertiesHelper);
    this.externalTriggerConsumer.init();
  }

  public static TaktClientBuilder newClientBuilder() {
    return new TaktClientBuilder();
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
