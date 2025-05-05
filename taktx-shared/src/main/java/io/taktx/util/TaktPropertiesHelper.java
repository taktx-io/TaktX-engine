package io.taktx.util;

import java.util.Properties;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

@Getter
public class TaktPropertiesHelper {

  private final String tenant;

  private final String namespace;

  private final Properties kafkaProperties;

  public TaktPropertiesHelper(String tenant, String namespace, Properties kafkaProperties) {
    this.tenant = tenant;
    this.namespace = namespace;
    this.kafkaProperties = kafkaProperties;
  }

  public Properties getKafkaConsumerProperties(
      String groupId,
      Class<?> keyDeserializer,
      Class<?> valueDeserializer,
      String autoOffsetResetConfig) {
    Properties props = new Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
    props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig);
    props.putAll(kafkaProperties);
    return props;
  }

  public Properties getKafkaProducerProperties(
      Class<? extends Serializer<?>> keySserializer,
      Class<? extends Serializer<?>> valueSerializer) {
    Properties props = new Properties();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySserializer.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
    props.putAll(kafkaProperties);
    return props;
  }

  public String getPrefixedTopicName(String topic) {
    return tenant + "." + namespace + "." + topic;
  }
}
