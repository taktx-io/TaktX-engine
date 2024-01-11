package nl.qunit.bpmnmeister.engine.pd;

import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import java.security.NoSuchAlgorithmException;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Path("/process-definitions")
public class ProcessDefinitionResource {

  @Channel("process-definition-xml-outgoing")
  Emitter<String> triggerEmitter;

  @Inject BpmnParser bpmnParser;

  @POST
  @Consumes(MediaType.APPLICATION_XML)
  public void add(String xml) throws JAXBException, NoSuchAlgorithmException {
    Definitions definitions = bpmnParser.parse(xml);
    triggerEmitter.send(KafkaRecord.of(definitions.getProcessDefinitionId(), xml));
  }
}
