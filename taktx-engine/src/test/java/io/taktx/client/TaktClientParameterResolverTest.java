package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class TaktClientParameterResolverTest {

  @Test
  void test() {
    Properties kafkaProperties = new Properties();
    kafkaProperties.put("bootstrap.servers", "localhost:9092");
    TaktClient client =
        TaktClient.newClientBuilder()
            .withNamespace("namespace")
            .withTenant("tenant")
            .withKafkaProperties(kafkaProperties)
            .build();
    TaktClientParameterResolver resolver = new TaktClientParameterResolver(client);
    Object resolve = resolver.resolve(null);
    assertThat(resolve).isEqualTo(client);
  }
}
