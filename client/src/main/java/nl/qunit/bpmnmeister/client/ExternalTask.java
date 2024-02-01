package nl.qunit.bpmnmeister.client;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.METHOD})
public @interface ExternalTask {
  String element();
}
