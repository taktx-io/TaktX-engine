package io.taktx.client;

import java.lang.reflect.Parameter;

public interface TaktParameterResolverFactory {

  TaktParameterResolver create(Parameter parameter);
}
