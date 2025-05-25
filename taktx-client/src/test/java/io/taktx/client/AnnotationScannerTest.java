package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.client.annotation.TaktDeployment;
import java.util.Set;
import org.junit.jupiter.api.Test;

@TaktDeployment(resource = "test")
class AnnotationScannerTest {

  @Test
  void testScan() {
    Set<TaktDeployment> taktDeployments = AnnotationScanner.findTaktDeployments();
    assertThat(taktDeployments).hasSize(1);

    TaktDeployment taktDeployment = taktDeployments.iterator().next();
    assertThat(taktDeployment.resource()).isEqualTo("test");
  }
}
