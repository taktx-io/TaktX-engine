package nl.qunit.bpmnmeister.client;

import io.quarkus.runtime.Startup;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@Startup
public class Deployer {
  @Inject BeanManager beanManager;

  @Channel("process-definition-xml-outgoing")
  Emitter<String> definitionEmitter;

  private final Map<BaseElementId, Object> definitionMap = new HashMap<>();

  @PostConstruct
  void init() throws IOException {
    // Scan all beans for BpmnDeployment annotations
    try {
      Set<Bean<?>> beans = beanManager.getBeans(Object.class, new AnnotationLiteral<Any>() {});
      for (Bean<?> bean : beans) {
        BpmnDeployment annotation = bean.getBeanClass().getAnnotation(BpmnDeployment.class);
        if (annotation != null) {
          Object beanInstance = getBean(bean.getBeanClass());
          String resource = annotation.resource();
          URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
          Path bpmnPath = Paths.get(url.getPath());
          String xml = Files.readString(bpmnPath);
          String filename = bpmnPath.getFileName().toString();
          Definitions definitions = BpmnParser.parse(xml);

          definitionMap.put(definitions.getDefinitionsKey().getProcessDefinitionId(), beanInstance);

          definitionEmitter.send(KafkaRecord.of(filename, xml));
        }
      }
    } catch (JAXBException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T> T getBean(Class<T> beanClass) {
    return CDI.current().select(beanClass).get();
  }

  public Map<BaseElementId, Object> getDefinitionMap() {
    return definitionMap;
  }
}
