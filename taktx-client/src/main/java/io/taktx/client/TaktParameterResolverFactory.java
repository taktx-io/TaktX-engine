package io.taktx.client;

import java.lang.reflect.Parameter;

public interface TaktParameterResolverFactory {

  TaktParameterResolver create(TaktClient taktClient, Parameter parameter);
}
