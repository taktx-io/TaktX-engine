package com.flomaestro.client;

import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import java.io.IOException;
import java.util.Set;

public class TaktClient {
  private final ExternalTriggerConsumer externalTriggerConsumer;
  private final ProcessInstanceProducer processInstanceProducer;

  public TaktClient(String tenant, String namespace) throws IOException {
    TaktPropertiesHelper kafkaPropertiesHelper = new TaktPropertiesHelper(tenant, namespace);
    this.externalTriggerConsumer = new ExternalTriggerConsumer(kafkaPropertiesHelper);
    this.processInstanceProducer = new ProcessInstanceProducer(kafkaPropertiesHelper);
  }

  public static TaktClientBuilder newClientBuilder() {
    return new TaktClientBuilder();
  }

  public void start() throws IOException {
    this.externalTriggerConsumer.init();
  }

  public void startProcess(String process) {
    processInstanceProducer.startProcess(process, VariablesDTO.empty());
  }

  public void startProcess(String process, VariablesDTO variables) {
    processInstanceProducer.startProcess(process, variables);
  }

  public Set<String> getProcessDefinitionConsumers() {
    return externalTriggerConsumer.getProcessDefinitionConsumers();
  }

  public static class TaktClientBuilder {
    private String tenant;
    private String namespace;

    private TaktClientBuilder() {
      this.tenant = System.getenv("TENANT");
      this.namespace = System.getenv("NAMESPACE");
    }

    public TaktClient build() throws IOException {
      if (tenant == null) {
        throw new IllegalArgumentException("TENANT environment variable is not set");
      }
      if (namespace == null) {
        throw new IllegalArgumentException("NAMESPACE environment variable is not set");
      }
      return new TaktClient(tenant, namespace);
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
