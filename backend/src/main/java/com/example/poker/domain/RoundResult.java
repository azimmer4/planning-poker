package com.example.poker.domain;

import java.util.List;

/**
 * @param groups       count per chosen card, in deck order
 * @param average      mean of numeric cards, {@code null} if none
 * @param mostFrequent card with the highest count; ties go to the later card in the deck
 */
public record RoundResult(List<CardCount> groups, Double average, Card mostFrequent) {
}
