package nl.qunit.bpmnmeister.pd.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class BaseElementTest {
  @Test
  void testAllSubTypesAreMappedToJackson() {
    Reflections reflections = new Reflections(BaseElementDTO.class.getPackageName());
    Set<Class<? extends BaseElementDTO>> allClasses = reflections.getSubTypesOf(BaseElementDTO.class);

    Set<Class<? extends BaseElementDTO>> nonAbstractClasses = allClasses.stream()
        .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers()))
        .collect(Collectors.toSet());

    JsonSubTypes jsonSubTypes = BaseElementDTO.class.getAnnotation(JsonSubTypes.class);
    JsonSubTypes.Type[] types = jsonSubTypes.value();

    for (Class<? extends BaseElementDTO> nonAbstractClass : nonAbstractClasses) {
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