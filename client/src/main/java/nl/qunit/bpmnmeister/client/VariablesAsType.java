package nl.qunit.bpmnmeister.client;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VariablesAsType {
}
