package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Stereotype;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@QuarkusTest
@QuarkusTestResource(ContainerKafkaTestResource.class)
@Stereotype
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DisabledIfSystemProperty(named = "container-enabled", matches = "false")
public @interface QuarkusContainerKafkaTest {}