package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.runtime.Startup;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.util.GenerationExtractor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Startup
public class Deployer {
    @Inject
    GenerationExtractor generationExtractor;
    @Inject
    BpmnParser bpmnParser;
    @Channel("process-definition-xml-outgoing")
    Emitter<String> triggerEmitter;

    private final Map<String, Map<String, String>> definitionMap = new HashMap<>();

    @PostConstruct
    void init() throws IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("bpmn");
        Path bpmnPath = Paths.get(url.getPath());
        Files.walk(bpmnPath).filter(Files::isRegularFile).forEach(
                file -> {
                    try {
                        String xml = Files.readString(file);
                        String filename = file.getFileName().toString();
                        String generation = generationExtractor.getGenerationFromString(filename).orElseThrow();
                        Definitions definitions = bpmnParser.parse(xml, generation);

                        Map<String, String> genMap = definitionMap.computeIfAbsent(definitions.getProcessDefinitionId(), k -> new HashMap<>());
                        genMap.put(generation, filename);
                        triggerEmitter.send(KafkaRecord.of(filename, xml));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JAXBException e) {
                        throw new RuntimeException(e);
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    public Map<String, Map<String, String>> getDefinitionMap() {
        return definitionMap;
    }
}
