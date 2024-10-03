package nl.qunit.bpmnmeister.pi.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class BpmnElementStateTest {
  @Test
  void testAllSubTypesAreMappedToJackson() {
    Reflections reflections = new Reflections(FlowNodeInstanceDTO.class.getPackageName());
    Set<Class<? extends FlowNodeInstanceDTO>> allClasses = reflections.getSubTypesOf(FlowNodeInstanceDTO.class);

    Set<Class<? extends FlowNodeInstanceDTO>> nonAbstractClasses = allClasses.stream()
        .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers()))
        .collect(Collectors.toSet());

    JsonSubTypes jsonSubTypes = FlowNodeInstanceDTO.class.getAnnotation(JsonSubTypes.class);
    JsonSubTypes.Type[] types = jsonSubTypes.value();

    for (Class<? extends FlowNodeInstanceDTO> nonAbstractClass : nonAbstractClasses) {
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