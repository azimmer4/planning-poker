package com.example.poker.domain;

import static com.example.poker.domain.Card.COFFEE;
import static com.example.poker.domain.Card.EIGHT;
import static com.example.poker.domain.Card.FIVE;
import static com.example.poker.domain.Card.ONE;
import static com.example.poker.domain.Card.QUESTION;
import static com.example.poker.domain.Card.THIRTY_FOUR;
import static com.example.poker.domain.Card.THREE;
import static com.example.poker.domain.Card.TWO;
import static com.example.poker.domain.Card.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultCalculatorTest {

    private final ResultCalculator calculator = new ResultCalculator();

    @Nested
    class Groups {

        @Test
        void countsVotesPerCardInDeckOrder() {
            var result = calculator.calculate(List.of(FIVE, THREE, FIVE, QUESTION, THREE, FIVE));

            assertEquals(List.of(
                    new CardCount(THREE, 2),
                    new CardCount(FIVE, 3),
                    new CardCount(QUESTION, 1)), result.groups());
        }

        @Test
        void containsOnlyCardsThatWereChosen() {
            var result = calculator.calculate(List.of(EIGHT));

            assertEquals(List.of(new CardCount(EIGHT, 1)), result.groups());
        }

        @Test
        void includesSpecialCards() {
            var result = calculator.calculate(List.of(COFFEE, QUESTION, COFFEE));

            assertEquals(List.of(new CardCount(QUESTION, 1), new CardCount(COFFEE, 2)), result.groups());
        }
    }

    @Nested
    class Average {

        @Test
        void isArithmeticMeanOfNumericCards() {
            var result = calculator.calculate(List.of(ONE, TWO, THREE, FIVE));

            assertEquals(2.75, result.average());
        }

        @Test
        void ignoresQuestionMarkAndCoffee() {
            var result = calculator.calculate(List.of(THREE, FIVE, QUESTION, COFFEE));

            assertEquals(4.0, result.average());
        }

        @Test
        void countsZeroAsNumericValue() {
            var result = calculator.calculate(List.of(ZERO, TWO));

            assertEquals(1.0, result.average());
        }

        @Test
        void isNullWhenOnlySpecialCardsWereChosen() {
            var result = calculator.calculate(List.of(QUESTION, COFFEE));

            assertNull(result.average());
        }
    }

    @Nested
    class MostFrequent {

        @Test
        void isCardWithHighestCount() {
            var result = calculator.calculate(List.of(THREE, FIVE, FIVE, EIGHT));

            assertEquals(FIVE, result.mostFrequent());
        }

        @Test
        void canBeSpecialCard() {
            var result = calculator.calculate(List.of(QUESTION, QUESTION, FIVE));

            assertEquals(QUESTION, result.mostFrequent());
        }

        @Test
        void tieIsResolvedByHighestNumericCard() {
            var result = calculator.calculate(List.of(THREE, THREE, EIGHT, EIGHT, ONE));

            assertEquals(EIGHT, result.mostFrequent());
        }

        @Test
        void tieRanksQuestionMarkAboveNumbers() {
            var result = calculator.calculate(List.of(THIRTY_FOUR, QUESTION));

            assertEquals(QUESTION, result.mostFrequent());
        }

        @Test
        void tieRanksCoffeeAboveQuestionMark() {
            var result = calculator.calculate(List.of(COFFEE, QUESTION, FIVE));

            assertEquals(COFFEE, result.mostFrequent());
        }
    }

    @Test
    void noVotesYieldEmptyResult() {
        var result = calculator.calculate(List.of());

        assertTrue(result.groups().isEmpty());
        assertNull(result.average());
        assertNull(result.mostFrequent());
    }
}
