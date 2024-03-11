package nl.qunit.bpmnmeister.util;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationExtractorTest {
    @ParameterizedTest
    @ValueSource(strings = {"123Ge.Gen123.xml", "Gen123", "AAAGen123", "123Gen123", "_Gen123", "Gen123.asdasd", "Gen123.", "abc_Gen123.asdad.asdasd", "123.Gen123.asdasd.asdasd"})
    void testCombinations(String input) {
        Optional<Integer> generation = GenerationExtractor.getGenerationFromString(input);
        assertThat(generation).contains(123);
    }

}