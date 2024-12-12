package com.flomaestro.engine.pi.testengine;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.microprofile.config.ConfigProvider;

public class KafkaConsumerUtil<K, V> {

  private final Consumer<V> consumer;
  private KafkaConsumer<K, V> kafkaConsumer;
  private boolean running = true;

  public KafkaConsumerUtil(
      String groupId,
      String topic,
      String keyDeserializerClass,
      String valueDeserializerClass,
      Consumer<V> consumer) {
    this.consumer = consumer;
    Properties props = new Properties();
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializerClass);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializerClass);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, topic + "-test-consumer");

    kafkaConsumer = new KafkaConsumer<>(props);
    kafkaConsumer.subscribe(Collections.singletonList(topic));
    start();
  }

  void start() {
    new Thread(
            () -> {
              while (running) {
                kafkaConsumer
                    .poll(100)
                    .forEach(
                        record -> {
                          V value = record.value();
                          consumer.accept(value);
                        });
              }
              try {
                if (kafkaConsumer != null) {
                  kafkaConsumer.close(Duration.ofMillis(100));
                }
                kafkaConsumer = null;
              } catch (Throwable t) {
                t.printStackTrace();
              }
            })
        .start();
  }

  void stop() {
    running = false;
  }
}
