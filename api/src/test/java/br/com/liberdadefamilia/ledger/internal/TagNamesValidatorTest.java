package br.com.liberdadefamilia.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TagNamesValidatorTest {

    private final TagNamesValidator validator = new TagNamesValidator();

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("null passes", null, true),
                Arguments.of("empty list passes", List.of(), true),
                Arguments.of("stripped name passes", List.of("  Escola "), true),
                Arguments.of("empty name fails", List.of(""), false),
                Arguments.of("blank name fails", List.of("   "), false),
                Arguments.of("30 characters pass", List.of("a".repeat(30)), true),
                Arguments.of("31 characters fail", List.of("a".repeat(31)), false),
                Arguments.of("control character fails", List.of("a\u0007"), false),
                Arguments.of("null name fails", Arrays.asList("a", null), false),
                Arguments.of("five distinct names pass, case aside", List.of("a", "A", "b", "c", "d", "e"), true),
                Arguments.of("six distinct names fail", List.of("a", "b", "c", "d", "e", "f"), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void checksNames(String label, List<String> names, boolean valid) {
        assertThat(validator.isValid(names, null)).isEqualTo(valid);
    }
}
