/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
