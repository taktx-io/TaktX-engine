package com.flomaestro.takt.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

@Getter
public class TaktPropertiesHelper {

  private final String tenant;

  private final String namespace;

  private final String kafkaBootstrapServers;

  private final Properties commonProperties;

  public TaktPropertiesHelper(String tenant, String namespace, String kafkaBootstrapServers)
      throws IOException {
    this.tenant = tenant;
    this.namespace = namespace;
    this.kafkaBootstrapServers = kafkaBootstrapServers;
    this.commonProperties = loadCommonProperties();
  }

  public Properties getKafkaConsumerProperties(
      String groupId, Class<?> keyDeserializer, Class<?> valueDeserializer) {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.putAll(commonProperties);
    return props;
  }

  public Properties loadCommonProperties() throws IOException {
    String taktKafkaPropertiesFile = System.getenv("TAKT_PROPERTIES_FILE");
    Properties properties = new Properties();
    if (taktKafkaPropertiesFile != null) {
      InputStream resourceAsStream = null;
      try {
        resourceAsStream = getClass().getResourceAsStream(taktKafkaPropertiesFile);
        if (resourceAsStream == null) {
          resourceAsStream = new FileInputStream(taktKafkaPropertiesFile);
        }
        properties.load(resourceAsStream);
      } finally {
        if (resourceAsStream != null) {
          resourceAsStream.close();
        }
      }
    }
    System.getenv()
        .forEach(
            (k, v) -> {
              if (k.startsWith("TAKT_KAFKA_")) {
                // remove the prefix and convert to lower case and replace underscores with dots
                String lowercaseKey =
                    k.substring("TAKT_KAFKA_".length()).toLowerCase().replace("_", ".");
                properties.put(lowercaseKey, v);
              }
            });
    return properties;
  }

  public Properties getKafkaProducerProperties(
      Class<? extends Serializer<?>> keySserializer,
      Class<? extends Serializer<?>> valueSerializer) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySserializer.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
    props.putAll(commonProperties);
    return props;
  }

  public String getPrefixedTopicName(String topic) {
    return tenant + "." + namespace + "." + topic;
  }
}
