package nl.qunit.bpmnmeister.pi;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class TriggerTest {
  @Test
  void testAllSubTypesAreMappedToJackson() {
    Reflections reflections = new Reflections(Trigger.class.getPackageName());
    Set<Class<? extends Trigger>> allClasses = reflections.getSubTypesOf(Trigger.class);

    Set<Class<? extends Trigger>> nonAbstractClasses = allClasses.stream()
        .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers()))
        .collect(Collectors.toSet());

    JsonSubTypes jsonSubTypes = Trigger.class.getAnnotation(JsonSubTypes.class);
    JsonSubTypes.Type[] types = jsonSubTypes.value();

    for (Class<? extends Trigger> nonAbstractClass : nonAbstractClasses) {
      boolean isMapped = false;
      for (JsonSubTypes.Type type : types) {
        if (type.value().equals(nonAbstractClass)) {
          isMapped = true;
          break;
        }
      }
      assertTrue(isMapped, nonAbstractClass.getName() + " is not mapped in JsonSubTypes annotation");
    }
  }
}