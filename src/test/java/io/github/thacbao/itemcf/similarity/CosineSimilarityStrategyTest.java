package io.github.thacbao.itemcf.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CosineSimilarityStrategyTest {

    private final CosineSimilarityStrategy strategy = CosineSimilarityStrategy.INSTANCE;

    @Test
    void identicalVectors_shouldReturnOne() {
        // Two items with the exact same user-score profile → similarity = 1.0
        Map<Integer, Double> v = Map.of(1, 5.0, 2, 3.0, 3, 4.0);
        assertThat(strategy.compute(v, v)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void noCommonUsers_shouldReturnZero() {
        Map<Integer, Double> v1 = Map.of(1, 5.0, 2, 3.0);
        Map<Integer, Double> v2 = Map.of(3, 4.0, 4, 2.0);
        assertThat(strategy.compute(v1, v2)).isEqualTo(0.0);
    }

    @Test
    void emptyVector_shouldReturnZero() {
        Map<Integer, Double> v1 = Map.of(1, 5.0);
        Map<Integer, Double> v2 = Map.of();
        assertThat(strategy.compute(v1, v2)).isEqualTo(0.0);
        assertThat(strategy.compute(v2, v1)).isEqualTo(0.0);
    }

    @Test
    void orthogonalVectors_shouldReturnZero() {
        // v1 and v2 share no common users — dot product on common users = 0
        Map<Integer, Double> v1 = Map.of(1, 5.0, 2, 0.0);
        Map<Integer, Double> v2 = Map.of(2, 0.0, 3, 3.0);
        // user 2 is common but both scores are 0, dot product = 0
        // norm1 = sqrt(25) = 5.0, but v2 has all zeros for common → effectively
        // orthogonal
        double result = strategy.compute(v1, v2);
        assertThat(result).isBetween(0.0, 1.0);
    }

    @Test
    void partialOverlap_shouldReturnExpectedRange() {
        // user 1: rated item A=5, item B=3
        // user 2: rated item A=4, item B=4
        // user 3: rated item A=2 only
        Map<Integer, Double> vA = Map.of(1, 5.0, 2, 4.0, 3, 2.0);
        Map<Integer, Double> vB = Map.of(1, 3.0, 2, 4.0);

        double sim = strategy.compute(vA, vB);

        // dot = 5*3 + 4*4 = 15 + 16 = 31
        // normA = sqrt(25 + 16 + 4) = sqrt(45), normB = sqrt(9 + 16) = sqrt(25) = 5
        // sim = 31 / (sqrt(45) * 5) ≈ 0.924
        assertThat(sim).isCloseTo(0.924, within(0.001));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("symmetryTestCases")
    void similarity_shouldBeSymmetric(String caseName, Map<Integer, Double> v1, Map<Integer, Double> v2) {
        assertThat(strategy.compute(v1, v2)).isCloseTo(strategy.compute(v2, v1), within(1e-10));
    }

    static Stream<Arguments> symmetryTestCases() {
        return Stream.of(
                Arguments.of("identical",
                        Map.of(1, 5.0, 2, 3.0),
                        Map.of(1, 5.0, 2, 3.0)),
                Arguments.of("single common user",
                        Map.of(1, 4.0, 2, 2.0),
                        Map.of(1, 3.0, 3, 5.0)),
                Arguments.of("no overlap",
                        Map.of(1, 5.0),
                        Map.of(2, 3.0)));
    }

    @Test
    void result_shouldAlwaysBeInRangeZeroToOne() {
        Map<Integer, Double> v1 = Map.of(1, 5.0, 2, 1.0, 3, 3.0);
        Map<Integer, Double> v2 = Map.of(1, 2.0, 2, 4.0, 4, 5.0);
        double result = strategy.compute(v1, v2);
        assertThat(result).isBetween(0.0, 1.0);
    }
}
