package com.flomaestro.engine.pi.testengine;

import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.ConfigProvider;

public class KafkaProducerUtil<K, V> {

  private final String topic;
  private KafkaProducer<K, V> producer;

  public KafkaProducerUtil(String topic, String keySerializerClass, String valueSerializerClass) {
    this.topic = topic;
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializerClass);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializerClass);
    props.put(ProducerConfig.CLIENT_ID_CONFIG, topic + "-test-producer");
    this.producer = new KafkaProducer<>(props);
  }

  public void send(K key, V value) {
    producer.send(new ProducerRecord<>(topic, key, value));
  }

  public void close() {
    try {
      if (producer != null) {
        producer.close(Duration.ofMillis(100));
      }
      producer = null;
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
