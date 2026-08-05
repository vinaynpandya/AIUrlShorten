package com.assignment.urlshortener.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator shortCodeGenerator = new ShortCodeGenerator();

    @Test
    void generatedCodeHasLengthSeven() {
        String code = shortCodeGenerator.generate();

        assertThat(code).hasSize(7);
    }

    @Test
    void generatedCodeMatchesAllowedAlphabet() {
        String code = shortCodeGenerator.generate();

        assertThat(code).matches("[0-9a-zA-Z]{7}");
    }

    @Test
    void generatedCodesAreNotAllIdentical() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(shortCodeGenerator.generate());
        }

        assertThat(codes.size()).isGreaterThan(1);
    }
}
