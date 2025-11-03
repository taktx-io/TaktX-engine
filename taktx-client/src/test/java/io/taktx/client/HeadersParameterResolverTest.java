package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class HeadersParameterResolverTest {

  @Test
  void testOriginalType() {
    HeadersParameterResolver resolver = new HeadersParameterResolver(new ObjectMapper(), Map.class);
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .headers(Map.of("key1", "value1", "key2", "value2"))
            .build();
    assertThat(resolver.resolve(trigger))
        .isInstanceOf(Map.class)
        .isEqualTo(Map.of("key1", "value1", "key2", "value2"));
  }

  @Test
  void testSupportedClassType() {
    HeadersParameterResolver resolver =
        new HeadersParameterResolver(new ObjectMapper(), MyTaskHeaders.class);
    ExternalTaskTriggerDTO trigger =
        ExternalTaskTriggerDTO.builder()
            .headers(Map.of("key1", "value1", "key2", "value2"))
            .build();
    assertThat(resolver.resolve(trigger))
        .isInstanceOf(MyTaskHeaders.class)
        .isEqualTo(new MyTaskHeaders("value1", "value2"));
  }

  static class MyTaskHeaders {
    private String key1;
    private String key2;

    public MyTaskHeaders() {}

    public MyTaskHeaders(String key1, String key2) {
      this.key1 = key1;
      this.key2 = key2;
    }

    public String getKey1() {
      return key1;
    }

    public void setKey1(String key1) {
      this.key1 = key1;
    }

    public String getKey2() {
      return key2;
    }

    public void setKey2(String key2) {
      this.key2 = key2;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MyTaskHeaders that = (MyTaskHeaders) o;
      return Objects.equals(key1, that.key1) && Objects.equals(key2, that.key2);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key1, key2);
    }
  }
}
