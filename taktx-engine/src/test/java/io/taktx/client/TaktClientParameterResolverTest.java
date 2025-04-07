package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaktClientParameterResolverTest {

  @Test
  void test() {
    TaktClient client =
        TaktClient.newClientBuilder()
            .withNamespace("namespace")
            .withTenant("tenant")
            .withBootstrapServers("localhost:9092")
            .build();
    TaktClientParameterResolver resolver = new TaktClientParameterResolver(client);
    Object resolve = resolver.resolve(null);
    assertThat(resolve).isEqualTo(client);
  }
}
