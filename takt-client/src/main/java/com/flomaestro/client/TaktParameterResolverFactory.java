package com.flomaestro.client;

import java.lang.reflect.Parameter;

public interface TaktParameterResolverFactory {

  TaktParameterResolver create(TaktClient taktClient, Parameter parameter);
}
