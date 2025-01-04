package com.flomaestro.takt.util;

import com.flomaestro.takt.Topics;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

public class TaktPropertiesHelper {

  private final String tenant;

  private final String namespace;

  public TaktPropertiesHelper(String tenant, String namespace) {
    this.tenant = tenant;
    this.namespace = namespace;
  }

  public Properties getKafkaConsumerProperties(
      String groupId, Class<?> keyDeserializer, Class<?> valueDeserializer) throws IOException {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    Properties commonProperties = gettCommonProperties();
    props.putAll(commonProperties);
    return props;
  }

  public Properties gettCommonProperties() throws IOException {
    String taktKafkaPropertiesFile = System.getenv("TAKT_PROPERTIES_FILE");
    Properties properties = new Properties();
    if (taktKafkaPropertiesFile != null) {
      InputStream resourceAsStream = getClass().getResourceAsStream(taktKafkaPropertiesFile);
      if (resourceAsStream == null) {
        resourceAsStream = new FileInputStream(taktKafkaPropertiesFile);
      }
      if (resourceAsStream == null) {
        throw new IllegalArgumentException(
            "Could not find properties file: " + taktKafkaPropertiesFile);
      }
      properties.load(resourceAsStream);
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
      Class<? extends Serializer<?>> keySserializer, Class<? extends Serializer<?>> valueSerializer)
      throws IOException {
    Properties props = new Properties();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySserializer.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
    Properties commonProperties = gettCommonProperties();
    props.putAll(commonProperties);
    return props;
  }

  public String getPrefixedTopicName(Topics topics) {
    return tenant + "." + namespace + "." + topics.getTopicName();
  }
}
