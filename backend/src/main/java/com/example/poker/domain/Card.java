package com.example.poker.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Planning-poker deck. Declaration order is the deck order (34 &lt; ? &lt; ☕) and is used for tie-breaking. */
public enum Card {
    ZERO("0", 0),
    ONE("1", 1),
    TWO("2", 2),
    THREE("3", 3),
    FIVE("5", 5),
    EIGHT("8", 8),
    THIRTEEN("13", 13),
    TWENTY_ONE("21", 21),
    THIRTY_FOUR("34", 34),
    QUESTION("?", null),
    COFFEE("☕", null);

    private final String value;
    private final Integer points;

    Card(String value, Integer points) {
        this.value = value;
        this.points = points;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Numeric value, or {@code null} for ? and ☕. */
    public Integer points() {
        return points;
    }

    public boolean isNumeric() {
        return points != null;
    }

    @JsonCreator
    public static Card fromValue(String value) {
        for (Card card : values()) {
            if (card.value.equals(value)) {
                return card;
            }
        }
        throw new IllegalArgumentException("Unknown card: " + value);
    }
}
