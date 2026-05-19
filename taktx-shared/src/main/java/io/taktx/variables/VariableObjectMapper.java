/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import io.taktx.proto.VariableValue;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared Java-object ↔ variable mapper used at the public API boundaries.
 *
 * <p>The mapper keeps protobuf as the canonical wire format and only uses reflection as a boundary
 * convenience for beans/records. Reflection metadata is cached per class to avoid repeated
 * introspection overhead on hot paths.
 */
public final class VariableObjectMapper {

  private static final Map<Class<?>, BeanMetadata> BEAN_METADATA_CACHE = new ConcurrentHashMap<>();
  private static final CopyOnWriteArrayList<VariableTypeAdapter> TYPE_ADAPTERS =
      new CopyOnWriteArrayList<>();

  static {
    registerDefaultAdapters();
  }

  private VariableObjectMapper() {}

  /** Registers a custom adapter. Newly registered adapters take precedence over built-ins. */
  public static void registerTypeAdapter(VariableTypeAdapter adapter) {
    TYPE_ADAPTERS.add(0, Objects.requireNonNull(adapter, "adapter"));
  }

  /** Returns whether the supplied type is handled as a scalar/simple type rather than a bean. */
  public static boolean isSimpleValueType(Class<?> type) {
    Class<?> boxed = box(type);
    return boxed == String.class
        || Number.class.isAssignableFrom(boxed)
        || boxed == Boolean.class
        || boxed == Character.class
        || boxed == byte[].class
        || boxed.isEnum()
        || findTypeAdapter(boxed) != null
        || VariableValue.class.isAssignableFrom(boxed);
  }

  /** Converts an arbitrary Java object to a {@link VariableValue}. */
  public static VariableValue toVariableValue(Object value) {
    return toVariableValue(value, new IdentityHashMap<>());
  }

  /** Converts a bean, record, or map-like object to a variable map. */
  public static Map<String, VariableValue> toVariableMap(Object source) {
    return toVariableMap(source, new IdentityHashMap<>());
  }

  /** Converts a variable map to a plain Java map. */
  public static Map<String, Object> toPlainJavaMap(Map<String, VariableValue> variables) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    if (variables != null) {
      variables.forEach((key, value) -> result.put(key, Variables.toJavaObject(value)));
    }
    return result;
  }

  /** Decodes a {@link VariableValue} into the requested Java target type. */
  public static Object fromVariableValue(VariableValue value, Class<?> targetType) {
    if (targetType == VariableValue.class) {
      return value;
    }
    return fromJavaObject(Variables.toJavaObject(value), targetType);
  }

  /** Decodes a variable map into the requested Java target type. */
  public static <T> T fromVariableMap(Map<String, VariableValue> variables, Class<T> targetType) {
    return targetType.cast(fromJavaObject(toPlainJavaMap(variables), targetType));
  }

  /** Converts a plain Java value into the requested target type. */
  public static Object fromJavaObject(Object value, Class<?> targetType) {
    if (targetType == null || targetType == Object.class) {
      return value;
    }
    Class<?> boxedTargetType = box(targetType);
    if (value == null) {
      return null;
    }
    if (boxedTargetType.isInstance(value)) {
      return value;
    }

    VariableTypeAdapter adapter = findTypeAdapter(boxedTargetType);
    if (adapter != null) {
      return adapter.fromJavaObject(value, boxedTargetType);
    }

    if (boxedTargetType == String.class) {
      return String.valueOf(value);
    }
    if (boxedTargetType == Long.class) {
      return toLong(value);
    }
    if (boxedTargetType == Integer.class) {
      return Math.toIntExact(toLong(value));
    }
    if (boxedTargetType == Short.class) {
      return (short) toLong(value);
    }
    if (boxedTargetType == Byte.class) {
      return (byte) toLong(value);
    }
    if (boxedTargetType == Double.class) {
      return toDouble(value);
    }
    if (boxedTargetType == Float.class) {
      return (float) toDouble(value);
    }
    if (boxedTargetType == Boolean.class) {
      return toBoolean(value);
    }
    if (boxedTargetType == Character.class) {
      String stringValue = String.valueOf(value);
      if (stringValue.length() != 1) {
        throw new IllegalArgumentException(
            "Cannot convert value '" + value + "' to Character: expected length 1");
      }
      return stringValue.charAt(0);
    }
    if (boxedTargetType.isEnum()) {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object enumValue = Enum.valueOf((Class<? extends Enum>) boxedTargetType, String.valueOf(value));
      return enumValue;
    }
    if (Map.class.isAssignableFrom(boxedTargetType) && value instanceof Map<?, ?>) {
      return value;
    }
    if (value instanceof Map<?, ?> map) {
      return instantiateFromMap(normalizeMap(map), boxedTargetType);
    }
    return instantiateFromMap(extractProperties(value), boxedTargetType);
  }

  private static VariableValue toVariableValue(
      Object value, IdentityHashMap<Object, Boolean> visiting) {
    if (value == null) {
      return Variables.nullValue();
    }
    if (value instanceof VariableValue variableValue) {
      return variableValue;
    }
    if (value instanceof Boolean bool) {
      return Variables.of(bool.booleanValue());
    }
    if (value instanceof Long longValue) {
      return Variables.of(longValue.longValue());
    }
    if (value instanceof Integer intValue) {
      return Variables.of((long) intValue.intValue());
    }
    if (value instanceof Short shortValue) {
      return Variables.of((long) shortValue.shortValue());
    }
    if (value instanceof Byte byteValue) {
      return Variables.of((long) byteValue.byteValue());
    }
    if (value instanceof Double doubleValue) {
      return Variables.of(doubleValue.doubleValue());
    }
    if (value instanceof Float floatValue) {
      return Variables.of((double) floatValue.floatValue());
    }
    if (value instanceof String stringValue) {
      return Variables.of(stringValue);
    }
    if (value instanceof Character characterValue) {
      return Variables.of(String.valueOf(characterValue));
    }
    if (value instanceof byte[] bytesValue) {
      return Variables.of(bytesValue);
    }
    if (value.getClass().isEnum()) {
      return Variables.of(((Enum<?>) value).name());
    }

    VariableTypeAdapter adapter = findTypeAdapter(value.getClass());
    if (adapter != null) {
      return adapter.toVariableValue(value);
    }

    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      io.taktx.proto.VarList.Builder listBuilder = io.taktx.proto.VarList.newBuilder();
      for (int i = 0; i < length; i++) {
        listBuilder.addItems(toVariableValue(Array.get(value, i), visiting));
      }
      return VariableValue.newBuilder().setListValue(listBuilder.build()).build();
    }
    if (value instanceof Iterable<?> iterable) {
      io.taktx.proto.VarList.Builder listBuilder = io.taktx.proto.VarList.newBuilder();
      for (Object item : iterable) {
        listBuilder.addItems(toVariableValue(item, visiting));
      }
      return VariableValue.newBuilder().setListValue(listBuilder.build()).build();
    }
    if (value instanceof Map<?, ?> map) {
      LinkedHashMap<String, VariableValue> result = new LinkedHashMap<>();
      map.forEach((key, mapValue) -> result.put(String.valueOf(key), toVariableValue(mapValue, visiting)));
      return VariableValue.newBuilder().setMapValue(Variables.toVarMap(result)).build();
    }

    enter(value, visiting);
    try {
      return VariableValue.newBuilder()
          .setMapValue(Variables.toVarMap(metadataFor(value.getClass()).read(value, visiting)))
          .build();
    } finally {
      visiting.remove(value);
    }
  }

  private static Map<String, VariableValue> toVariableMap(
      Object source, IdentityHashMap<Object, Boolean> visiting) {
    if (source == null) {
      return Map.of();
    }
    if (source instanceof Map<?, ?> map) {
      LinkedHashMap<String, VariableValue> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), toVariableValue(value, visiting)));
      return result;
    }
    enter(source, visiting);
    try {
      return metadataFor(source.getClass()).read(source, visiting);
    } finally {
      visiting.remove(source);
    }
  }

  private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
    if (visiting.put(value, Boolean.TRUE) != null) {
      throw new IllegalArgumentException(
          "Cannot convert cyclic object graph rooted at " + value.getClass().getName());
    }
  }

  private static BeanMetadata metadataFor(Class<?> type) {
    if (isSimpleValueType(type)
        || Map.class.isAssignableFrom(type)
        || Iterable.class.isAssignableFrom(type)
        || type.isArray()) {
      throw new IllegalArgumentException(
          "Expected a bean/record/map for variable mapping but got " + type.getName());
    }
    return BEAN_METADATA_CACHE.computeIfAbsent(type, VariableObjectMapper::inspectBeanMetadata);
  }

  private static BeanMetadata inspectBeanMetadata(Class<?> type) {
    if (type.isRecord()) {
      return inspectRecord(type);
    }
    return inspectBean(type);
  }

  private static BeanMetadata inspectRecord(Class<?> type) {
    RecordComponent[] components = type.getRecordComponents();
    Constructor<?> canonicalConstructor =
        findRecordConstructor(type, Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));

    List<PropertyReader> readers = new ArrayList<>(components.length);
    LinkedHashMap<String, RecordComponentBinding> bindings = new LinkedHashMap<>();
    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      Method accessor = makeAccessible(component.getAccessor());
      readers.add(new PropertyReader(component.getName(), accessor));
      bindings.put(
          component.getName(), new RecordComponentBinding(i, component.getType(), component.getName()));
    }
    readers.sort((left, right) -> left.name.compareTo(right.name));
    return new RecordBeanMetadata(readers, canonicalConstructor, bindings, components.length);
  }

  private static BeanMetadata inspectBean(Class<?> type) {
    try {
      Constructor<?> constructor = findNoArgsConstructor(type);
      PropertyDescriptor[] descriptors = Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors();
      List<PropertyReader> readers = new ArrayList<>();
      LinkedHashMap<String, BeanWriter> writers = new LinkedHashMap<>();
      for (PropertyDescriptor descriptor : descriptors) {
        Method readMethod = descriptor.getReadMethod();
        if (readMethod != null) {
          readers.add(new PropertyReader(descriptor.getName(), makeAccessible(readMethod)));
        }
        Method writeMethod = descriptor.getWriteMethod();
        if (writeMethod != null) {
          writers.put(
              descriptor.getName(), new BeanWriter(makeAccessible(writeMethod), descriptor.getPropertyType()));
        }
      }
      if (readers.isEmpty()) {
        throw new IllegalArgumentException("No readable bean properties found on " + type.getName());
      }
      readers.sort((left, right) -> left.name.compareTo(right.name));
      return new JavaBeanMetadata(readers, constructor, writers, type);
    } catch (java.beans.IntrospectionException e) {
      throw new IllegalArgumentException("Cannot inspect bean properties for " + type.getName(), e);
    }
  }

  private static Constructor<?> findNoArgsConstructor(Class<?> type) {
    try {
      return makeAccessible(type.getDeclaredConstructor());
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Constructor<?> findRecordConstructor(Class<?> type, Class<?>[] parameterTypes) {
    try {
      return makeAccessible(type.getDeclaredConstructor(parameterTypes));
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot access canonical record constructor for " + type.getName(), e);
    }
  }

  private static <T extends java.lang.reflect.AccessibleObject> T makeAccessible(T accessibleObject) {
    accessibleObject.setAccessible(true);
    return accessibleObject;
  }

  private static Map<String, Object> normalizeMap(Map<?, ?> source) {
    LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
    source.forEach((key, value) -> normalized.put(String.valueOf(key), value));
    return normalized;
  }

  private static Object instantiateFromMap(Map<String, Object> source, Class<?> targetType) {
    BeanMetadata metadata = metadataFor(targetType);
    return metadata.instantiate(source);
  }

  private static Map<String, Object> extractProperties(Object bean) {
    if (bean instanceof Map<?, ?> map) {
      return normalizeMap(map);
    }
    if (isSimpleValueType(bean.getClass()) || bean.getClass().isArray() || bean instanceof Collection<?>) {
      throw new IllegalArgumentException(
          "Expected a bean/record/map for variable mapping but got " + bean.getClass().getName());
    }
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    metadataFor(bean.getClass())
        .read(bean, new IdentityHashMap<>())
        .forEach((key, value) -> result.put(key, Variables.toJavaObject(value)));
    return result;
  }

  private static long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private static double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private static boolean toBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    return type;
  }

  private static VariableTypeAdapter findTypeAdapter(Class<?> type) {
    for (VariableTypeAdapter adapter : TYPE_ADAPTERS) {
      if (adapter.supports(type)) {
        return adapter;
      }
    }
    return null;
  }

  private static void registerDefaultAdapters() {
    TYPE_ADAPTERS.add(new ExactTypeAdapter(UUID.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return UUID.fromString(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(Instant.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(((Instant) value).toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return Instant.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(LocalDate.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return LocalDate.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(LocalDateTime.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return LocalDateTime.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(OffsetDateTime.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return OffsetDateTime.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(ZonedDateTime.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return ZonedDateTime.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(Duration.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        return Duration.parse(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(URI.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        try {
          return new URI(String.valueOf(value));
        } catch (URISyntaxException e) {
          throw new IllegalArgumentException("Cannot convert value '" + value + "' to URI", e);
        }
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(URL.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(value.toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        try {
          return new URL(String.valueOf(value));
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("Cannot convert value '" + value + "' to URL", e);
        }
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(BigInteger.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(((BigInteger) value).toString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        if (value instanceof Number number) {
          return BigInteger.valueOf(number.longValue());
        }
        return new BigInteger(String.valueOf(value));
      }
    });
    TYPE_ADAPTERS.add(new ExactTypeAdapter(BigDecimal.class) {
      @Override
      public VariableValue toVariableValue(Object value) {
        return Variables.of(((BigDecimal) value).toPlainString());
      }

      @Override
      public Object fromJavaObject(Object value, Class<?> targetType) {
        if (value instanceof BigDecimal decimal) {
          return decimal;
        }
        return new BigDecimal(String.valueOf(value));
      }
    });
  }

  private abstract static class ExactTypeAdapter implements VariableTypeAdapter {

    private final Class<?> type;

    private ExactTypeAdapter(Class<?> type) {
      this.type = type;
    }

    @Override
    public boolean supports(Class<?> candidateType) {
      return type == candidateType;
    }
  }

  private abstract static class BeanMetadata {

    private final List<PropertyReader> readers;

    private BeanMetadata(List<PropertyReader> readers) {
      this.readers = readers;
    }

    private Map<String, VariableValue> read(Object source, IdentityHashMap<Object, Boolean> visiting) {
      LinkedHashMap<String, VariableValue> values = new LinkedHashMap<>();
      for (PropertyReader reader : readers) {
        try {
          values.put(reader.name, toVariableValue(reader.accessor.invoke(source), visiting));
        } catch (ReflectiveOperationException e) {
          throw new IllegalArgumentException(
              "Cannot read property '" + reader.name + "' from " + source.getClass().getName(), e);
        }
      }
      return values;
    }

    abstract Object instantiate(Map<String, Object> source);
  }

  private static final class RecordBeanMetadata extends BeanMetadata {

    private final Constructor<?> constructor;
    private final Map<String, RecordComponentBinding> bindings;
    private final int componentCount;

    private RecordBeanMetadata(
        List<PropertyReader> readers,
        Constructor<?> constructor,
        Map<String, RecordComponentBinding> bindings,
        int componentCount) {
      super(readers);
      this.constructor = constructor;
      this.bindings = bindings;
      this.componentCount = componentCount;
    }

    @Override
    Object instantiate(Map<String, Object> source) {
      Object[] arguments = new Object[componentCount];
      bindings.forEach(
          (name, binding) ->
              arguments[binding.index] = fromJavaObject(source.get(name), binding.type));
      try {
        return constructor.newInstance(arguments);
      } catch (ReflectiveOperationException e) {
        throw new IllegalArgumentException(
            "Cannot map " + source.getClass().getName() + " to " + constructor.getDeclaringClass().getName(), e);
      }
    }
  }

  private static final class JavaBeanMetadata extends BeanMetadata {

    private final Constructor<?> constructor;
    private final Map<String, BeanWriter> writers;
    private final Class<?> type;

    private JavaBeanMetadata(
        List<PropertyReader> readers,
        Constructor<?> constructor,
        Map<String, BeanWriter> writers,
        Class<?> type) {
      super(readers);
      this.constructor = constructor;
      this.writers = writers;
      this.type = type;
    }

    @Override
    Object instantiate(Map<String, Object> source) {
      if (constructor == null) {
        throw new IllegalArgumentException(
            "Cannot map " + source.getClass().getName() + " to " + type.getName() + ": no no-arg constructor available");
      }
      try {
        Object instance = constructor.newInstance();
        for (Map.Entry<String, BeanWriter> entry : writers.entrySet()) {
          if (!source.containsKey(entry.getKey())) {
            continue;
          }
          BeanWriter writer = entry.getValue();
          Object convertedValue = fromJavaObject(source.get(entry.getKey()), writer.type);
          writer.method.invoke(instance, convertedValue);
        }
        return instance;
      } catch (ReflectiveOperationException e) {
        throw new IllegalArgumentException(
            "Cannot map " + source.getClass().getName() + " to " + type.getName(), e);
      }
    }
  }

  private record PropertyReader(String name, Method accessor) {}

  private record BeanWriter(Method method, Class<?> type) {}

  private record RecordComponentBinding(int index, Class<?> type, String name) {}
}





