package nl.qunit.bpmnmeister.client;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        this.type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public Type getType() {
        return this.type;
    }

    @SuppressWarnings("unchecked")
    public Class<T> getTypeClass() {
            return (Class<T>) type;
    }
}
