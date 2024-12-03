package nl.qunit.bpmnmeister.engine.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

@ApplicationScoped
public class ClientProducer {

  @Produces
  public Client createClient() {
    return ClientBuilder.newClient();
  }
}
