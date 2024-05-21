package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestPath;

@Path("/process")
public class ProcessResource {
  @Inject ObjectMapper objectMapper;
  @Inject KafkaPropertiesHelper kafkaPropertiesHelper;

  @ConfigProperty(name = "deployer.kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "deployer.kafka.sasl.mechanism")
  Optional<String> kafkaSaslMechanism;

  @ConfigProperty(name = "deployer.kafka.sasl.jaas.config")
  Optional<String> kafkaSaslJaasConfig;

  @ConfigProperty(name = "deployer.kafka.security.protocol")
  Optional<String> kafkaSecurityProtocol;

  @POST
  @Path("/{processId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void start(@RestPath String processId, String variables) {
    // Convert Json string to Map of variables with JsonNode values
    Map<String, JsonNode> variablesMap;
    try {
      variablesMap = objectMapper.readValue(variables, LinkedHashMap.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse variables", e);
    }
    StartCommand startCommand =
        new StartCommand(
            ProcessInstanceKey.NONE, Constants.NONE, Constants.NONE, processId, new Variables(variablesMap));
    KafkaProducer<String, StartCommand> startCommandEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, StartCommandSerializer.class));
    startCommandEmitter.send(
        new ProducerRecord<>(Topics.DEFINITIONS_TOPIC.getTopicName(), processId, startCommand));
    startCommandEmitter.flush();
    startCommandEmitter.close();
  }
}
