package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import java.io.IOException;
import java.util.StringJoiner;

public class CustomTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return idFromClass(value.getClass());
  }

  @Override
  public String idFromValueAndType(Object o, Class<?> suggestedType) {
    return idFromClass(suggestedType);
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CUSTOM;
  }

  private String idFromClass(Class<?> clazz) {
    String packageName = clazz.getPackage().getName();
    String[] packageParts = packageName.split("\\.");
    String versionPackagePart = packageParts[packageParts.length - 1];
    String className = clazz.getSimpleName();
    return new StringJoiner(".").add(versionPackagePart).add(className).toString();
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    String[] parts = id.split("\\.");
    String className = parts[1];
    String packageName = "com.flomaestro.takt.dto." + parts[0];
    try {
      return SimpleType.constructUnsafe(Class.forName(packageName + "." + className));
    } catch (ClassNotFoundException e) {
      throw new IOException("Class not found for type id: " + id, e);
    }
  }
}
