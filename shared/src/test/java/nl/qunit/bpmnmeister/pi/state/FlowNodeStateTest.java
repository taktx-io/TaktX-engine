package nl.qunit.bpmnmeister.pi.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class FlowNodeStateTest {
  @Test
  void testAllSubTypesAreMappedToJackson() {
    Reflections reflections = new Reflections(FlowNodeStateDTO.class.getPackageName());
    Set<Class<? extends FlowNodeStateDTO>> allClasses = reflections.getSubTypesOf(FlowNodeStateDTO.class);

    Set<Class<? extends FlowNodeStateDTO>> nonAbstractClasses = allClasses.stream()
        .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers()))
        .collect(Collectors.toSet());

    JsonSubTypes jsonSubTypes = FlowNodeStateDTO.class.getAnnotation(JsonSubTypes.class);
    JsonSubTypes.Type[] types = jsonSubTypes.value();

    for (Class<? extends FlowNodeStateDTO> nonAbstractClass : nonAbstractClasses) {
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