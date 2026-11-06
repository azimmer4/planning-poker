package com.example.poker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CardTest {

    @Test
    void deckHasFibonacciOrderWithSpecialCardsLast() {
        var values = Arrays.stream(Card.values()).map(Card::value).toList();

        assertEquals(List.of("0", "1", "2", "3", "5", "8", "13", "21", "34", "?", "☕"), values);
    }

    @Test
    void parsesCardFromItsValue() {
        assertEquals(Card.THIRTEEN, Card.fromValue("13"));
        assertEquals(Card.QUESTION, Card.fromValue("?"));
        assertEquals(Card.COFFEE, Card.fromValue("☕"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "100", "-1", "", "2.75", "coffee"})
    void rejectsValuesOutsideTheDeck(String value) {
        assertThrows(IllegalArgumentException.class, () -> Card.fromValue(value));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Card.fromValue(null));
    }
}
