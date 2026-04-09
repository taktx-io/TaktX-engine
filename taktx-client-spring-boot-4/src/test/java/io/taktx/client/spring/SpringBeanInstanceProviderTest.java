/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class SpringBeanInstanceProviderTest {

  private ApplicationContext applicationContext;
  private SpringBeanInstanceProvider provider;

  @BeforeEach
  void setUp() {
    applicationContext = mock(ApplicationContext.class);
    provider = new SpringBeanInstanceProvider(applicationContext);
  }

  @Test
  void testGetInstance_returnsBean() {
    // Given
    TestBean testBean = new TestBean();
    when(applicationContext.getBean(TestBean.class)).thenReturn(testBean);

    // When
    TestBean result = provider.getInstance(TestBean.class);

    // Then
    assertThat(result).isSameAs(testBean);
  }

  @Test
  void testGetInstance_differentTypes() {
    // Given
    TestBean testBean = new TestBean();
    AnotherBean anotherBean = new AnotherBean();
    when(applicationContext.getBean(TestBean.class)).thenReturn(testBean);
    when(applicationContext.getBean(AnotherBean.class)).thenReturn(anotherBean);

    // When
    TestBean result1 = provider.getInstance(TestBean.class);
    AnotherBean result2 = provider.getInstance(AnotherBean.class);

    // Then
    assertThat(result1).isSameAs(testBean);
    assertThat(result2).isSameAs(anotherBean);
  }

  // Test helper classes
  static class TestBean {}

  static class AnotherBean {}
}
