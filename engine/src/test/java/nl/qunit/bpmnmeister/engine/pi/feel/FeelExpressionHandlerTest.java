package nl.qunit.bpmnmeister.engine.pi.feel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import nl.qunit.bpmnmeister.pi.Variables;
import org.junit.jupiter.api.Test;

class FeelExpressionHandlerTest {

  @Test
  void testExpressionSuccessNoVars() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=1 + 1", Variables.EMPTY);
    assertThat(jsonNode.asInt()).isEqualTo(2);
  }

  @Test
  void testExpressionFailureNoVars() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=1 +", Variables.EMPTY);
    assertThat(jsonNode).isNull();
  }

  @Test
  void testExpressionSuccessWithSimpleVar() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=1 + a", Variables.of("a", 5));
    assertThat(jsonNode.asInt()).isEqualTo(6);
  }

  @Test
  void testExpressionSuccessStringConcat() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=\"abc\" + a", Variables.of("a", "value"));
    assertThat(jsonNode.asText()).isEqualTo("abcvalue");
  }

  @Test
  void testExpressionSuccessDifferentTypeConcat() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=string(1) + a", Variables.of("a", "value"));
    assertThat(jsonNode.asText()).isEqualTo("1"
                                                    + "value");
  }

  @Test
  void testExpressionSuccessCallFunction() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=floor(1.4)", Variables.EMPTY);
    assertThat(jsonNode.asInt()).isEqualTo(1);
  }

  @Test
  void testExpressionSuccessWithComplexVar() {
    FeelExpressionHandler feelExpressionHandler = new FeelExpressionHandler(new FeelEngineProvider());
    JsonNode jsonNode = feelExpressionHandler.processFeelExpression("=1 + a.subVar.i", Variables.of("a", new ComplexVar(8)));
    assertThat(jsonNode.asInt()).isEqualTo(9);
  }

  public static class ComplexVar {
    private SubVar subVar;

    public ComplexVar() {

    }
    public ComplexVar(int i) {
      this.subVar = new SubVar(i);
    }

    public void setSubVar(SubVar subVar) {
      this.subVar = subVar;
    }

    public SubVar getSubVar() {
      return subVar;
    }
  }

  public static  class SubVar {

    private  Integer i;

    public SubVar() {
    }

    public SubVar(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }

    public void setI(int i) {
      this.i = i;
    }
  }
}